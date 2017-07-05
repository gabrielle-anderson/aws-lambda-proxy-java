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
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toConcurrentMap;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class MethodHandlerTest {
    private SampleMethodHandler sampleMethodHandler;
    private ApiGatewayProxyRequest request;
    private ContentTypeMapper<Integer> contentTypeMapper1;
    private ContentTypeMapper<Integer> contentTypeMapper2;
    private ContentTypeMapper<Integer> contentTypeMapper3;
    private AcceptMapper<Integer> acceptMapper1;
    private AcceptMapper<Integer> acceptMapper2;
    private AcceptMapper<Integer> acceptMapper3;
    private Context context;

    private static final MediaType CONTENT_TYPE_1 = new MediaType("application", "ContentType1");
    private static final MediaType CONTENT_TYPE_2 = new MediaType("application", "ContentType2");
    private static final MediaType CONTENT_TYPE_3 = new MediaType("application", "ContentType3");
    private static final MediaType ACCEPT_TYPE_1 = new MediaType("application", "AcceptType1");
    private static final MediaType ACCEPT_TYPE_2 = new MediaType("application", "AcceptType2");
    private static final MediaType ACCEPT_TYPE_3 = new MediaType("application", "AcceptType3");

    @Before
    public void setup() {
        sampleMethodHandler = new SampleMethodHandler(new HashSet<String>());
        context = mock(Context.class);
        request = new ApiGatewayProxyRequestBuilder()
                .withContext(context)
                .build();
        TestingLogger testingLogger = new TestingLogger();
        when(context.getLogger()).thenReturn(testingLogger);

        contentTypeMapper1 = mock(ContentTypeMapper.class);
        contentTypeMapper2 = mock(ContentTypeMapper.class);
        contentTypeMapper3 = mock(ContentTypeMapper.class);
        acceptMapper1 = mock(AcceptMapper.class);
        acceptMapper2 = mock(AcceptMapper.class);
        acceptMapper3 = mock(AcceptMapper.class);
    }

    @Test
    public void shouldReturnUnsupportedMediaTypeIfNoContentTypeMapperRegistered() throws Exception {
        List<MediaType> contentTypes = asList(CONTENT_TYPE_1, CONTENT_TYPE_2);

        ApiGatewayProxyResponse response = sampleMethodHandler.handle(request, contentTypes, singletonList(ACCEPT_TYPE_1), context);

        assertThat(response.getStatusCode()).isEqualTo(UNSUPPORTED_MEDIA_TYPE.getStatusCode());
        assertThat(response.getBody()).contains(String.format("Content-Types %s are not supported", contentTypes));
    }

    @Test
    public void shouldReturnUnsupportedMediaTypeIfNoAcceptMapperRegistered() throws Exception {
        sampleMethodHandler.registerPerContentType(CONTENT_TYPE_1, contentTypeMapper1);
        List<MediaType> acceptTypes = asList(ACCEPT_TYPE_1, ACCEPT_TYPE_2);

        ApiGatewayProxyResponse response = sampleMethodHandler.handle(request, singletonList(CONTENT_TYPE_1), acceptTypes, context);

        assertThat(response.getStatusCode()).isEqualTo(UNSUPPORTED_MEDIA_TYPE.getStatusCode());
        assertThat(response.getBody()).contains(String.format("Accept types %s are not supported", acceptTypes));
    }

    @Test(expected = RuntimeException.class)
    public void shouldRethrowExceptionWhenExceptionNotRegistered() throws Exception {
        sampleMethodHandler.registerPerContentType(CONTENT_TYPE_1, contentTypeMapper1);
        sampleMethodHandler.registerPerAccept(ACCEPT_TYPE_1, acceptMapper1);
        when(contentTypeMapper1.toInput(request, context)).thenThrow(new RuntimeException());

        sampleMethodHandler.handle(request, singletonList(CONTENT_TYPE_1), singletonList(ACCEPT_TYPE_1), context);
    }

    @Test
    public void shouldHandleExceptionWhenRegistered() throws Exception {
        sampleMethodHandler.registerPerContentType(CONTENT_TYPE_1, contentTypeMapper1);
        sampleMethodHandler.registerPerAccept(ACCEPT_TYPE_1, acceptMapper1);
        final ApiGatewayProxyResponse expectedResponse = new ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder()
                .withStatusCode(NOT_FOUND.getStatusCode())
                .build();
        sampleMethodHandler.registerExceptionMap(RuntimeException.class, e -> expectedResponse);
        when(contentTypeMapper1.toInput(request, context)).thenThrow(new RuntimeException());

        ApiGatewayProxyResponse response = sampleMethodHandler.handle(request, singletonList(CONTENT_TYPE_1), singletonList(ACCEPT_TYPE_1), context);

        assertThat(response).isEqualTo(expectedResponse);
    }

    @Test
    public void shouldReturnSuccessIfContentTypeAndAcceptRegistered() throws Exception {
        sampleMethodHandler.registerPerContentType(CONTENT_TYPE_1, contentTypeMapper1);
        sampleMethodHandler.registerPerAccept(ACCEPT_TYPE_1, acceptMapper1);
        int output = 0;
        when(contentTypeMapper1.toInput(request, context)).thenReturn(output);
        when(acceptMapper1.outputToResponse(output)).thenReturn(new ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder()
                .withStatusCode(OK.getStatusCode())
                .build());

        ApiGatewayProxyResponse response = sampleMethodHandler.handle(request, singletonList(CONTENT_TYPE_1), singletonList(ACCEPT_TYPE_1), context);

        assertThat(response.getStatusCode()).isEqualTo(OK.getStatusCode());
    }

    @Test
    public void shouldReturnSuccessOfFirstAvailableAcceptAndContentTypesIfContentTypeAndAcceptRegistered() throws Exception {
        sampleMethodHandler.registerPerContentType(CONTENT_TYPE_1, contentTypeMapper1);
        sampleMethodHandler.registerPerContentType(CONTENT_TYPE_3, contentTypeMapper3);
        sampleMethodHandler.registerPerAccept(ACCEPT_TYPE_1, acceptMapper1);
        sampleMethodHandler.registerPerAccept(ACCEPT_TYPE_3, acceptMapper3);
        int output = 0;
        when(contentTypeMapper3.toInput(request, context)).thenReturn(output);
        when(acceptMapper3.outputToResponse(output)).thenReturn(new ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder()
                .withStatusCode(OK.getStatusCode())
                .build());

        sampleMethodHandler.handle(request, asList(CONTENT_TYPE_2, CONTENT_TYPE_3), asList(ACCEPT_TYPE_2, ACCEPT_TYPE_3), context);

        verify(contentTypeMapper3).toInput(request, context);
        verify(acceptMapper3).outputToResponse(output);
    }

    @Test
    public void shouldReturnOkIfRequiredHeadersArePresent() throws Exception {
        Collection<String> requiredHeaders = Stream.of("header1", "header2")
                .map(Util::randomizeCase)
                .collect(toList());
        SampleMethodHandler sampleMethodHandlerWithRequiredHeaders = new SampleMethodHandler(requiredHeaders);
        sampleMethodHandlerWithRequiredHeaders.registerPerContentType(CONTENT_TYPE_1, contentTypeMapper1);
        sampleMethodHandlerWithRequiredHeaders.registerPerAccept(ACCEPT_TYPE_1, acceptMapper1);
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
        when(contentTypeMapper1.toInput(requestWithRequiredHeaders, context)).thenReturn(output);
        when(acceptMapper1.outputToResponse(output)).thenReturn(new ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder()
                .withStatusCode(OK.getStatusCode())
                .build());

        ApiGatewayProxyResponse response = sampleMethodHandlerWithRequiredHeaders.handle(requestWithRequiredHeaders, singletonList(CONTENT_TYPE_1), singletonList(ACCEPT_TYPE_1), context);

        assertThat(response.getStatusCode()).isEqualTo(OK.getStatusCode());
    }

    @Test
    public void shouldReturnBadRequestIfRequiredHeadersAreNotPresent() throws Exception {
        List<String> requiredHeaders = Stream.of("header1", "header2")
                .map(Util::randomizeCase)
                .collect(toList());
        SampleMethodHandler sampleMethodHandlerWithRequiredHeaders = new SampleMethodHandler(requiredHeaders);
        sampleMethodHandlerWithRequiredHeaders.registerPerContentType(CONTENT_TYPE_1, contentTypeMapper1);
        sampleMethodHandlerWithRequiredHeaders.registerPerAccept(ACCEPT_TYPE_1, acceptMapper1);
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
        when(contentTypeMapper1.toInput(requestWithRequiredHeaders, context)).thenReturn(output);
        when(acceptMapper1.outputToResponse(output)).thenReturn(new ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder()
                .withStatusCode(OK.getStatusCode())
                .build());

        ApiGatewayProxyResponse response = sampleMethodHandlerWithRequiredHeaders.handle(requestWithRequiredHeaders, singletonList(CONTENT_TYPE_1), singletonList(ACCEPT_TYPE_1), context);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST.getStatusCode());
        assertThat(response.getBody()).contains("The following required headers are not present: " + requiredHeaders.get(lastElementIndex).toLowerCase());
    }
}