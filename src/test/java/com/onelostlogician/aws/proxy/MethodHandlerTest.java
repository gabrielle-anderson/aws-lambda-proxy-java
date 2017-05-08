package com.onelostlogician.aws.proxy;

import com.amazonaws.services.lambda.runtime.Context;
import com.onelostlogician.aws.proxy.fixtures.ApiGatewayProxyRequestBuilder;
import com.onelostlogician.aws.proxy.fixtures.SampleMethodHandler;
import com.onelostlogician.aws.proxy.fixtures.TestingLogger;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.onelostlogician.aws.proxy.Util.randomiseKeyValues;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toConcurrentMap;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static javax.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MethodHandlerTest {
    private SampleMethodHandler sampleMethodHandler;
    private ApiGatewayProxyRequest request;
    private ContentTypeMapper<Integer> contentTypeMapper;
    private AcceptMapper<Integer> acceptMapper;
    private Context context;

    private static final MediaType CONTENT_TYPE = APPLICATION_JSON_TYPE;
    private static final MediaType ACCEPT = TEXT_PLAIN_TYPE;

    @Before
    public void setup() {
        sampleMethodHandler = new SampleMethodHandler(new HashSet<String>());
        context = mock(Context.class);
        request = new ApiGatewayProxyRequestBuilder()
                .withContext(context)
                .build();
        TestingLogger testingLogger = new TestingLogger();
        when(context.getLogger()).thenReturn(testingLogger);

        contentTypeMapper = mock(ContentTypeMapper.class);
        acceptMapper = mock(AcceptMapper.class);
    }

    @Test
    public void shouldReturnUnsupportedMediaTypeIfNoContentTypeMapperRegistered() throws Exception {
        ApiGatewayProxyResponse response = sampleMethodHandler.handle(request, CONTENT_TYPE, ACCEPT, context);

        assertThat(response.getStatusCode()).isEqualTo(UNSUPPORTED_MEDIA_TYPE.getStatusCode());
        assertThat(response.getBody()).contains(String.format("Content-Type %s is not supported", CONTENT_TYPE));
    }

    @Test
    public void shouldReturnUnsupportedMediaTypeIfNoAcceptMapperRegistered() throws Exception {
        sampleMethodHandler.registerPerContentType(CONTENT_TYPE, contentTypeMapper);

        ApiGatewayProxyResponse response = sampleMethodHandler.handle(request, CONTENT_TYPE, ACCEPT, context);

        assertThat(response.getStatusCode()).isEqualTo(UNSUPPORTED_MEDIA_TYPE.getStatusCode());
        assertThat(response.getBody()).contains(String.format("Accept %s is not supported", ACCEPT));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRethrowExceptionWhenExceptionNotRegistered() throws Exception {
        sampleMethodHandler.registerPerContentType(CONTENT_TYPE, contentTypeMapper);
        sampleMethodHandler.registerPerAccept(ACCEPT, acceptMapper);
        when(contentTypeMapper.toInput(request, context)).thenThrow(new RuntimeException());

        sampleMethodHandler.handle(request, CONTENT_TYPE, ACCEPT, context);
    }

    @Test
    public void shouldHandleExceptionWhenRegistered() throws Exception {
        sampleMethodHandler.registerPerContentType(CONTENT_TYPE, contentTypeMapper);
        sampleMethodHandler.registerPerAccept(ACCEPT, acceptMapper);
        final ApiGatewayProxyResponse expectedResponse = new ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder()
                .withStatusCode(NOT_FOUND.getStatusCode())
                .build();
        sampleMethodHandler.registerExceptionMap(RuntimeException.class, e -> expectedResponse);
        when(contentTypeMapper.toInput(request, context)).thenThrow(new RuntimeException());

        ApiGatewayProxyResponse response = sampleMethodHandler.handle(request, CONTENT_TYPE, ACCEPT, context);

        assertThat(response).isEqualTo(expectedResponse);
    }

    @Test
    public void shouldReturnSuccessIfContentTypeAndAcceptRegistered() throws Exception {
        sampleMethodHandler.registerPerContentType(CONTENT_TYPE, contentTypeMapper);
        sampleMethodHandler.registerPerAccept(ACCEPT, acceptMapper);
        int output = 0;
        when(contentTypeMapper.toInput(request, context)).thenReturn(output);
        when(acceptMapper.outputToResponse(output)).thenReturn(new ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder()
                .withStatusCode(OK.getStatusCode())
                .build());

        ApiGatewayProxyResponse response = sampleMethodHandler.handle(request, CONTENT_TYPE, ACCEPT, context);

        assertThat(response.getStatusCode()).isEqualTo(OK.getStatusCode());
    }

    @Test
    public void shouldReturnOkIfRequiredHeadersArePresent() throws Exception {
        Collection<String> requiredHeaders = Stream.of("header1", "header2")
                .map(Util::randomizeCase)
                .collect(toList());
        SampleMethodHandler sampleMethodHandlerWithRequiredHeaders = new SampleMethodHandler(requiredHeaders);
        sampleMethodHandlerWithRequiredHeaders.registerPerContentType(CONTENT_TYPE, contentTypeMapper);
        sampleMethodHandlerWithRequiredHeaders.registerPerAccept(ACCEPT, acceptMapper);
        int output = 0;
        Map<String, String> headers = requiredHeaders.stream().collect(toConcurrentMap(
                identity(),
                header -> RandomStringUtils.randomAlphabetic(5)
        ));
        randomiseKeyValues(headers);
        ApiGatewayProxyRequest requestWithRequiredHeaders = new ApiGatewayProxyRequestBuilder()
                .withContext(context)
                .withHeaders(headers)
                .build();
        when(contentTypeMapper.toInput(requestWithRequiredHeaders, context)).thenReturn(output);
        when(acceptMapper.outputToResponse(output)).thenReturn(new ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder()
                .withStatusCode(OK.getStatusCode())
                .build());

        ApiGatewayProxyResponse response = sampleMethodHandlerWithRequiredHeaders.handle(requestWithRequiredHeaders, CONTENT_TYPE, ACCEPT, context);

        assertThat(response.getStatusCode()).isEqualTo(OK.getStatusCode());
    }

    @Test
    public void shouldReturnBadRequestIfRequiredHeadersAreNotPresent() throws Exception {
        List<String> requiredHeaders = Stream.of("header1", "header2")
                .map(Util::randomizeCase)
                .collect(toList());
        SampleMethodHandler sampleMethodHandlerWithRequiredHeaders = new SampleMethodHandler(requiredHeaders);
        sampleMethodHandlerWithRequiredHeaders.registerPerContentType(CONTENT_TYPE, contentTypeMapper);
        sampleMethodHandlerWithRequiredHeaders.registerPerAccept(ACCEPT, acceptMapper);
        int output = 0;
        int lastElementIndex = requiredHeaders.size() - 1;
        Map<String, String> headers = requiredHeaders.subList(0, lastElementIndex).stream().collect(toConcurrentMap(
                identity(),
                header -> RandomStringUtils.randomAlphabetic(5)
        ));
        randomiseKeyValues(headers);
        ApiGatewayProxyRequest requestWithRequiredHeaders = new ApiGatewayProxyRequestBuilder()
                .withContext(context)
                .withHeaders(headers)
                .build();
        when(contentTypeMapper.toInput(requestWithRequiredHeaders, context)).thenReturn(output);
        when(acceptMapper.outputToResponse(output)).thenReturn(new ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder()
                .withStatusCode(OK.getStatusCode())
                .build());

        ApiGatewayProxyResponse response = sampleMethodHandlerWithRequiredHeaders.handle(requestWithRequiredHeaders, CONTENT_TYPE, ACCEPT, context);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST.getStatusCode());
        assertThat(response.getBody()).contains("The following required headers are not present: " + requiredHeaders.get(lastElementIndex).toLowerCase());
    }
}