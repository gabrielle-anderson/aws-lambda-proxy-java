package com.onelostlogician.aws.proxy;

import com.amazonaws.services.lambda.runtime.Context;
import org.apache.log4j.Logger;

import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;

import static com.onelostlogician.aws.proxy.ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder;
import static java.util.Objects.requireNonNull;
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
        this.requiredHeaders = requiredHeaders;
    }

    protected MethodHandler() {
        this(new HashSet<>());
    }

    public void registerPerContentType(MediaType mediaType, ContentTypeMapper<Input> contentTypeMapper) {
        perContentTypeMap.put(mediaType, contentTypeMapper);
    }

    public void registerPerAccept(MediaType mediaType, AcceptMapper<Output> acceptMapper) {
        perAcceptMap.put(mediaType, acceptMapper);
    }

    public <E extends Exception> void registerExceptionMap(Class<E> clazz, Function<E, ApiGatewayProxyResponse> exceptionMapper) {
        exceptionMap.put(clazz, exceptionMapper);
    }

    public abstract Output handle(Input input) throws Exception;

    public ApiGatewayProxyResponse handle(ApiGatewayProxyRequest request, MediaType contentType, MediaType accept, Context context) throws Exception {
        ApiGatewayProxyResponse response;
        try {
            if (!perContentTypeMap.containsKey(contentType)) {
                ApiGatewayProxyResponse unsupportedContentType = new ApiGatewayProxyResponseBuilder()
                                .withStatusCode(UNSUPPORTED_MEDIA_TYPE.getStatusCode())
                                .withBody(String.format("Content-Type %s is not supported", contentType))
                                .build();
                throw new LambdaException(unsupportedContentType);
            }
            if (!perAcceptMap.containsKey(accept)) {
                ApiGatewayProxyResponse unsupportedContentType = new ApiGatewayProxyResponseBuilder()
                        .withStatusCode(UNSUPPORTED_MEDIA_TYPE.getStatusCode())
                        .withBody(String.format("Accept %s is not supported", accept))
                        .build();
                throw new LambdaException(unsupportedContentType);
            }
            if (!request.getHeaders().keySet().containsAll(requiredHeaders)) {
                Collection<String> missingHeaders = new HashSet<>(requiredHeaders);
                missingHeaders.removeAll(request.getHeaders().keySet());
                ApiGatewayProxyResponse missingRequiredHeaders =
                        new ApiGatewayProxyResponseBuilder()
                                .withStatusCode(BAD_REQUEST.getStatusCode())
                                .withBody(String.format("The following required headers are not present: %s",
                                        String.join(", ", missingHeaders)))
                                .build();
                throw new LambdaException(missingRequiredHeaders);
            }

            ContentTypeMapper<Input> contentTypeMapper = perContentTypeMap.get(contentType);
            logger.debug("Content-Type mapper found.");
            AcceptMapper<Output> acceptMapper = perAcceptMap.get(accept);
            logger.debug("Accept mapper found.");

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
