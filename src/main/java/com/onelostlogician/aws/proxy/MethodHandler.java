package com.onelostlogician.aws.proxy;

import com.amazonaws.services.lambda.runtime.Context;
import com.google.common.net.MediaType;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.function.Function;

import static com.onelostlogician.aws.proxy.ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.UNSUPPORTED_MEDIA_TYPE;

public abstract class MethodHandler<Input, Output> {
    private final Logger logger = Logger.getLogger(getClass());

    private final Map<MediaType, ContentTypeMapper<Input>> perContentTypeMap;
    private final Map<MediaType, AcceptMapper<Output>> perAcceptMap;
    private final Map<Class<? extends Exception>, Function<? extends Exception, ApiGatewayProxyResponse>> exceptionMap;

    protected final Collection<String> requiredHeaders;

    protected MethodHandler(Collection<String> requiredHeaders) {
        this.perContentTypeMap = new HashMap<>();
        this.perAcceptMap = new HashMap<>();
        this.exceptionMap = new HashMap<>();
        this.exceptionMap.put(LambdaException.class, (Function<LambdaException, ApiGatewayProxyResponse>) LambdaException::getResponse);
        this.requiredHeaders = requiredHeaders.stream()
                .map(String::toLowerCase)
                .collect(toList());
    }

    protected MethodHandler() {
        this(new HashSet<>());
    }

    public void registerPerContentType(MediaType mediaType, ContentTypeMapper<Input> contentTypeMapper) {
        perContentTypeMap.put(mediaType.withoutParameters(), contentTypeMapper);
    }

    public void registerPerAccept(MediaType mediaType, AcceptMapper<Output> acceptMapper) {
        perAcceptMap.put(mediaType.withoutParameters(), acceptMapper);
    }

    public <E extends Exception> void registerExceptionMap(Class<E> clazz, Function<E, ApiGatewayProxyResponse> exceptionMapper) {
        exceptionMap.put(clazz, exceptionMapper);
    }

    public abstract Output handle(Input input) throws Exception;

    public ApiGatewayProxyResponse handle(ApiGatewayProxyRequest request, List<MediaType> contentTypes, List<MediaType> acceptTypes, Context context) throws Exception {
        ApiGatewayProxyResponse response;
        try {
            ContentTypeMapper<Input> contentTypeMapper = getMapper(contentTypes, perContentTypeMap, "Content-Types %s are not supported");
            logger.debug("Content-Type mapper found.");

            AcceptMapper<Output> acceptMapper = getMapper(acceptTypes, perAcceptMap, "Accept types %s are not supported");
            logger.debug("Accept mapper found.");

            Map<String, String> headers = request.getHeaders().entrySet().stream()
                    .collect(toMap(
                            entry -> entry.getKey().toLowerCase(),
                            entry -> entry.getValue().toLowerCase()
                    ));
            if (!headers.keySet().containsAll(requiredHeaders)) {
                Collection<String> missingHeaders = new HashSet<>(requiredHeaders);
                missingHeaders.removeAll(headers.keySet());
                ApiGatewayProxyResponse missingRequiredHeaders =
                        new ApiGatewayProxyResponseBuilder()
                                .withStatusCode(BAD_REQUEST.getStatusCode())
                                .withBody(String.format("The following required headers are not present: %s",
                                        String.join(", ", missingHeaders)))
                                .build();
                throw new LambdaException(missingRequiredHeaders);
            }


            logger.debug(String.format("Mapping input (%s): %s", contentTypeMapper.getClass(), request));
            Input input = requireNonNull(contentTypeMapper.toInput(request, context));
            logger.debug(String.format("Handling input (%s): %s", this.getClass(), input));
            Output output = requireNonNull(handle(input));
            logger.debug(String.format("Mapping output (%s): %s", acceptMapper.getClass(), output));
            response = requireNonNull(acceptMapper.outputToResponse(output));
            logger.debug("Successfully created response: " + response);
        } catch(Exception e) {
            response = handleException(e);
        }

        return response;
    }

    private static <T> T getMapper(List<MediaType> contentTypes, Map<MediaType, T> contentTypeMap, String errorMessage) throws LambdaException {
        List<MediaType> contentTypesWithoutParameters = contentTypes.stream()
                .map(MediaType::withoutParameters)
                .collect(toList());
        return contentTypesWithoutParameters.stream()
                .map(contentTypeMap::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> {
                    ApiGatewayProxyResponse unsupportedContentType = new ApiGatewayProxyResponseBuilder()
                            .withStatusCode(UNSUPPORTED_MEDIA_TYPE.getStatusCode())
                            .withBody(String.format(errorMessage, contentTypes))
                            .build();
                    return new LambdaException(unsupportedContentType);
                });
    }

    public Collection<String> getRequiredHeaders() {
        return requiredHeaders;
    }

    private <E extends Exception> ApiGatewayProxyResponse handleException(E exception) throws E {
        if (!exceptionMap.containsKey(exception.getClass())) {
            throw exception;
        }

        Function<E, ApiGatewayProxyResponse> lambdaResponseFunction = (Function<E, ApiGatewayProxyResponse>) exceptionMap.get(exception.getClass());
        return lambdaResponseFunction.apply(exception);
    }
}
