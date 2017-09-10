package com.onelostlogician.aws.proxy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.google.common.net.MediaType;
import com.googlecode.junittoolbox.ParallelParameterized;
import com.onelostlogician.aws.proxy.fixtures.ApiGatewayProxyRequestBuilder;
import com.onelostlogician.aws.proxy.fixtures.SampleMethodHandler;
import com.onelostlogician.aws.proxy.fixtures.TestingLogger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static com.onelostlogician.aws.proxy.Util.randomiseKeyValues;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.*;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(ParallelParameterized.class)
public class LambdaProxyHandlerTest {
    private static final MediaType CONTENT_TYPE_1 = MediaType.create("application", "ContentType1");
    private static final MediaType CONTENT_TYPE_2 = MediaType.create("application", "ContentType2");
    private static final MediaType ACCEPT_TYPE_1 = MediaType.create("application", "AcceptType1");
    private static final MediaType ACCEPT_TYPE_2 = MediaType.create("application", "AcceptType2");
    private static final String METHOD = "GET";
    private static final String ACCESS_CONTROL_REQUEST_METHOD = "Access-Control-Request-Method";
    private static final String ACCESS_CONTROL_REQUEST_HEADERS = "Access-Control-Request-Headers";

    private final LambdaProxyHandler<Configuration> handler;

    private Configuration configuration = mock(Configuration.class);
    private Context context = mock(Context.class);
    private LambdaLogger logger = new TestingLogger();
    private MethodHandler methodHandler = mock(MethodHandler.class);

    public LambdaProxyHandlerTest(boolean corsSupport) {
        handler = new TestLambdaProxyHandler(corsSupport);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Stream.of(true, false)
                .map(b -> singleton(b).toArray())
                .collect(toList());
    }

    @Before
    public void setup() {
        when(context.getLogger()).thenReturn(logger);
    }

    @Test
    public void shouldReturnBadRequestIfMethodNotRegistered() throws IOException, ParseException {
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod("GET")
                .withHeaders(new ConcurrentHashMap<>())
                .withContext(context)
                .build();
        ApiGatewayProxyResponse response = handler.handleRequest(request, context);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST.getStatusCode());
        assertThat(response.getBody()).isEqualTo(String.format("Lambda cannot handle the method %s", METHOD.toLowerCase()));
    }

    @Test
    public void shouldReturnServerErrorAndReasonWhenMisconfigured() throws ParseException {
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod(METHOD)
                .build();
        LambdaProxyHandler<Configuration> handlerWithFailingConguration = new TestLambdaProxyHandlerWithFailingConguration();
        handlerWithFailingConguration.registerMethodHandler(METHOD, c -> methodHandler);

        ApiGatewayProxyResponse actual = handlerWithFailingConguration.handleRequest(request, context);

        assertThat(actual).isNotNull();
        assertThat(actual.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR.getStatusCode());
        JSONParser jsonParser = new JSONParser();
        JSONObject jsonObject = (JSONObject) jsonParser.parse(actual.getBody());
        assertThat(jsonObject.keySet()).contains("message", "cause");
        assertThat((String) jsonObject.get("message")).contains("This service is mis-configured. Please contact your system administrator.");
        assertThat((String) jsonObject.get("cause")).contains("NullPointerException");
    }

    @Test
    public void shouldReturnBadRequestIfNoHeadersSpecified() throws IOException, ParseException {
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod(METHOD)
                .withContext(context)
                .build();

        ApiGatewayProxyResponse actual = handler.handleRequest(request, context);

        assertThat(actual.getStatusCode()).isEqualTo(BAD_REQUEST.getStatusCode());
    }

    @Test
    public void shouldReturnUnsupportedMediaTypeIfContentTypeNotSpecified() throws IOException, ParseException {
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod(METHOD)
                .withHeaders(new ConcurrentHashMap<>())
                .withContext(context)
                .build();
        handler.registerMethodHandler(METHOD, c -> methodHandler);

        ApiGatewayProxyResponse response = handler.handleRequest(request, context);

        assertThat(response.getStatusCode()).isEqualTo(UNSUPPORTED_MEDIA_TYPE.getStatusCode());
        assertThat(response.getBody()).isEqualTo(String.format("No %s header", CONTENT_TYPE.toLowerCase()));
    }

    @Test
    public void shouldReturnUnsupportedMediaTypeIfAcceptNotSpecified() throws IOException, ParseException {
        Map<String, String> headers = new ConcurrentHashMap<>();
        headers.put(CONTENT_TYPE, CONTENT_TYPE_1.toString());
        randomiseKeyValues(headers);
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod(METHOD)
                .withHeaders(headers)
                .withContext(context)
                .build();
        handler.registerMethodHandler(METHOD, c -> methodHandler);

        ApiGatewayProxyResponse response = handler.handleRequest(request, context);

        assertThat(response.getStatusCode()).isEqualTo(UNSUPPORTED_MEDIA_TYPE.getStatusCode());
        assertThat(response.getBody()).isEqualTo(String.format("No %s header", ACCEPT.toLowerCase()));
    }

    @Test
    public void shouldReturnBadRequestForMalformedMediaTypes() throws Exception {
        String someHeader = "someHeader";
        String someValue = "someValue";
        Map<String, String> requestHeaders = new ConcurrentHashMap<>();
        requestHeaders.put(CONTENT_TYPE, "MalformedContentType");
        requestHeaders.put(ACCEPT, ACCEPT_TYPE_1.toString());
        requestHeaders.put(someHeader, someValue);
        randomiseKeyValues(requestHeaders);
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod(METHOD)
                .withHeaders(requestHeaders)
                .withContext(context)
                .build();
        handler.registerMethodHandler(METHOD, c -> methodHandler);

        ApiGatewayProxyResponse response = handler.handleRequest(request, context);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST.getStatusCode());
        assertThat(response.getBody()).contains("Malformed media type");
    }

    @Test
    public void shouldReturnResponseFromMethodHandler() throws Exception {
        String someHeader = "someHeader";
        String someValue = "someValue";
        Map<String, String> requestHeaders = new ConcurrentHashMap<>();
        requestHeaders.put(CONTENT_TYPE, CONTENT_TYPE_1.toString());
        requestHeaders.put(ACCEPT, ACCEPT_TYPE_1.toString());
        requestHeaders.put(someHeader, someValue);
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod(METHOD)
                .withHeaders(requestHeaders)
                .withContext(context)
                .build();
        Map<String, String> responseHeaders = new ConcurrentHashMap<>();
        responseHeaders.put(someHeader, someValue);
        when(methodHandler.handle(request, singletonList(CONTENT_TYPE_1), singletonList(ACCEPT_TYPE_1), context))
                .thenReturn(new ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder()
                        .withStatusCode(OK.getStatusCode())
                        .withHeaders(responseHeaders)
                        .build());
        handler.registerMethodHandler(METHOD, c -> methodHandler);

        ApiGatewayProxyResponse response = handler.handleRequest(request, context);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(OK.getStatusCode());
        assertThat(response.getBody()).isEqualTo("");
        assertThat(response.getHeaders()).isEqualTo(responseHeaders);
    }

    @Test
    public void shouldParseSeparatedContentTypes() throws Exception {
        String someHeader = "someHeader";
        String someValue = "someValue";
        Map<String, String> requestHeaders = new ConcurrentHashMap<>();
        requestHeaders.put(CONTENT_TYPE, CONTENT_TYPE_1.toString() + ", " + CONTENT_TYPE_2.toString());
        requestHeaders.put(ACCEPT, ACCEPT_TYPE_1.toString() + ", " + ACCEPT_TYPE_2.toString());
        requestHeaders.put(someHeader, someValue);
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod(METHOD)
                .withHeaders(requestHeaders)
                .withContext(context)
                .build();
        Map<String, String> responseHeaders = new ConcurrentHashMap<>();
        responseHeaders.put(someHeader, someValue);
        List<MediaType> contentTypes = asList(CONTENT_TYPE_1, CONTENT_TYPE_2);
        List<MediaType> acceptTypes = asList(ACCEPT_TYPE_1, ACCEPT_TYPE_2);
        when(methodHandler.handle(eq(request), eq(contentTypes), eq(acceptTypes), eq(context)))
                .thenReturn(new ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder()
                        .withStatusCode(OK.getStatusCode())
                        .withHeaders(responseHeaders)
                        .build());
        handler.registerMethodHandler(METHOD, c -> methodHandler);

        handler.handleRequest(request, context);

        verify(methodHandler).handle(request, contentTypes, acceptTypes, context);
    }

    @Test
    public void shouldMapContentTypeParameters() throws Exception {
        String someHeader = "someHeader";
        String someValue = "someValue";
        Map<String, String> requestHeaders = new ConcurrentHashMap<>();
        MediaType contentType = CONTENT_TYPE_1.withParameter("q", "0.9");
        contentType = contentType.withParameter("b", "hello");
        String mediaTypeParametersString = contentType.parameters().asMap().entrySet().stream()
                .map(entry -> ";" + entry.getKey() + "=" + entry.getValue().iterator().next())
                .collect(joining(""));
        requestHeaders.put(CONTENT_TYPE, CONTENT_TYPE_1.toString() + mediaTypeParametersString + ", " + CONTENT_TYPE_2.toString());
        requestHeaders.put(ACCEPT, ACCEPT_TYPE_1.toString() + ", " + ACCEPT_TYPE_2.toString());
        requestHeaders.put(someHeader, someValue);
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod(METHOD)
                .withHeaders(requestHeaders)
                .withContext(context)
                .build();
        Map<String, String> responseHeaders = new ConcurrentHashMap<>();
        responseHeaders.put(someHeader, someValue);
        List<MediaType> contentTypes = asList(contentType, CONTENT_TYPE_2);
        List<MediaType> acceptTypes = asList(ACCEPT_TYPE_1, ACCEPT_TYPE_2);
        when(methodHandler.handle(request, contentTypes, acceptTypes, context)
        )
        .thenReturn(new ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder()
                .withStatusCode(OK.getStatusCode())
                .withHeaders(responseHeaders)
                .build());
        handler.registerMethodHandler(METHOD, c -> methodHandler);

        handler.handleRequest(request, context);

        verify(methodHandler).handle(request, contentTypes, acceptTypes, context);
    }

    @Test
    public void shouldReturnResponseFromMethodHandlerWithDifferentlyCasedContentTypeAndAccept() throws Exception {
        String someHeader = "someHeader";
        String someValue = "someValue";
        Map<String, String> requestHeaders = new ConcurrentHashMap<>();
        requestHeaders.put(CONTENT_TYPE, CONTENT_TYPE_1.toString().toUpperCase());
        requestHeaders.put(ACCEPT, ACCEPT_TYPE_1.toString().toUpperCase());
        requestHeaders.put(someHeader, someValue);
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod(METHOD)
                .withHeaders(requestHeaders)
                .withContext(context)
                .build();
        Map<String, String> responseHeaders = new ConcurrentHashMap<>();
        responseHeaders.put(someHeader, someValue);
        when(methodHandler.handle(request, singletonList(CONTENT_TYPE_1), singletonList(ACCEPT_TYPE_1), context))
                .thenReturn(new ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder()
                        .withStatusCode(OK.getStatusCode())
                        .withHeaders(responseHeaders)
                        .build());
        handler.registerMethodHandler(METHOD, c -> methodHandler);

        ApiGatewayProxyResponse response = handler.handleRequest(request, context);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(OK.getStatusCode());
        assertThat(response.getBody()).isEqualTo("");
        assertThat(response.getHeaders()).isEqualTo(responseHeaders);
    }

    @Test
    public void shouldPassThroughErrorMessageFromMethodHandlerInvocationIfDebug() throws Exception {
        Map<String, String> headers = new ConcurrentHashMap<>();
        headers.put(CONTENT_TYPE, CONTENT_TYPE_1.toString());
        headers.put(ACCEPT, ACCEPT_TYPE_1.toString());
        randomiseKeyValues(headers);
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod(METHOD)
                .withHeaders(headers)
                .withContext(context)
                .build();
        String message = "Some message";
        RuntimeException cause = new RuntimeException();
        StackTraceElement[] expectedStackTrace = new StackTraceElement[2];
        String declaringClass1 = "declaringClass1";
        String methodName1 = "methodName1";
        String fileName1 = "fileName1";
        int lineNumber1 = 1;
        expectedStackTrace[0] = new StackTraceElement(declaringClass1, methodName1, fileName1, lineNumber1);
        String declaringClass2 = "declaringClass2";
        String methodName2 = "methodName2";
        String fileName2 = "fileName2";
        int lineNumber2 = 2;
        expectedStackTrace[1] = new StackTraceElement(declaringClass2, methodName2, fileName2, lineNumber2);
        cause.setStackTrace(expectedStackTrace);
        when(methodHandler.handle(request, singletonList(CONTENT_TYPE_1), singletonList(ACCEPT_TYPE_1), context))
                .thenThrow(new RuntimeException(message, cause));
        handler.registerMethodHandler(METHOD, c -> methodHandler);

        ApiGatewayProxyResponse response = handler.handleRequest(request, context);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR.getStatusCode());
        JSONParser jsonParser = new JSONParser();
        JSONObject jsonObject = (JSONObject) jsonParser.parse(response.getBody());
        assertThat(jsonObject.keySet()).contains("message", "cause");
        assertThat(jsonObject.get("message")).isEqualTo(message);
        assertThat((String) jsonObject.get("cause")).contains(String.format("%s.%s(%s:%s)", declaringClass1, methodName1, fileName1, lineNumber1));
        assertThat((String) jsonObject.get("cause")).contains(String.format("%s.%s(%s:%s)", declaringClass2, methodName2, fileName2, lineNumber2));
    }

    @Test
    public void corsSupportShouldReturnOkForRegisteredMethodAndMediaTypes() {
        LambdaProxyHandler<Configuration> handlerWithCORSSupport = new TestLambdaProxyHandler(true);
        String methodBeingInvestigated = "GET";
        Collection<String> supportedMethods = asList(methodBeingInvestigated, "POST");
        MediaType mediaType1 = MediaType.create("application", "type1");
        MediaType mediaType2 = MediaType.create("application", "type2");
        MediaType mediaType3 = MediaType.create("application", "type3");
        MediaType mediaType4 = MediaType.create("application", "type4");
        Collection<String> requiredHeaders = Stream.of("header1", "header2")
                .map(Util::randomizeCase)
                .collect(toList());
        SampleMethodHandler sampleMethodHandler = new SampleMethodHandler(requiredHeaders);
        sampleMethodHandler.registerPerAccept(mediaType1, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerAccept(mediaType2, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerAccept(mediaType3, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerContentType(mediaType4, mock(ContentTypeMapper.class));
        supportedMethods.forEach(method -> handlerWithCORSSupport.registerMethodHandler(
                method,
                c -> sampleMethodHandler
        ));
        Map<String, String> headers = new ConcurrentHashMap<>();
        headers.put("Access-Control-Request-Method", methodBeingInvestigated);
        Collection<String> requestHeaders = requiredHeaders.stream()
                .map(Util::randomizeCase)
                .collect(toSet());
        requestHeaders.add("header3");
        headers.put("Access-Control-Request-Headers", String.join(", ", requestHeaders));
        headers.put("Content-Type", mediaType4.toString());
        randomiseKeyValues(headers);
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod("OPTIONS")
                .withHeaders(headers)
                .withContext(context)
                .build();

        ApiGatewayProxyResponse response = handlerWithCORSSupport.handleRequest(request, context);

        assertThat(response.getStatusCode()).isEqualTo(OK.getStatusCode());
        Map<String, String> responseHeaders = response.getHeaders();
        assertThat(responseHeaders).containsKey("Access-Control-Allow-Origin");
        assertThat(responseHeaders.get("Access-Control-Allow-Origin")).isEqualTo("*");
        assertThat(responseHeaders).containsKey("Access-Control-Allow-Headers");
        assertThat(asList(responseHeaders.get("Access-Control-Allow-Headers").split(", ")))
                .containsAll(requestHeaders.stream().map(String::toLowerCase).collect(toList()));
        assertThat(responseHeaders).containsKey("Access-Control-Allow-Methods");
        assertThat(asList(responseHeaders.get("Access-Control-Allow-Methods").split(", "))).containsAll(supportedMethods.stream().map(String::toLowerCase).collect(toList()));
    }

    @Test
    public void corsSupportShouldReturnBadRequestWhenRequestDoesNotSpecifyMethod() {
        LambdaProxyHandler<Configuration> handlerWithCorsSupport = new TestLambdaProxyHandler(true);
        Collection<String> supportedMethods = singletonList("POST");
        MediaType mediaType1 = MediaType.create("application", "type1");
        MediaType mediaType2 = MediaType.create("application", "type2");
        MediaType mediaType3 = MediaType.create("application", "type3");
        MediaType mediaType4 = MediaType.create("application", "type4");
        Collection<String> requiredHeaders = Stream.of("header1", "header2")
                .map(Util::randomizeCase)
                .collect(toList());
        SampleMethodHandler sampleMethodHandler = new SampleMethodHandler(requiredHeaders);
        sampleMethodHandler.registerPerAccept(mediaType1, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerAccept(mediaType2, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerAccept(mediaType3, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerContentType(mediaType4, mock(ContentTypeMapper.class));
        supportedMethods.forEach(method -> handlerWithCorsSupport.registerMethodHandler(
                method,
                c -> sampleMethodHandler
        ));
        Map<String, String> headers = new ConcurrentHashMap<>();
        Collection<String> requestHeaders = requiredHeaders.stream()
                .map(Util::randomizeCase)
                .collect(toSet());
        requestHeaders.add("header3");
        headers.put(ACCESS_CONTROL_REQUEST_HEADERS, String.join(", ", requestHeaders));
        headers.put("Content-Type", mediaType4.toString());
        randomiseKeyValues(headers);
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod("OPTIONS")
                .withHeaders(headers)
                .withContext(context)
                .build();

        ApiGatewayProxyResponse response = handlerWithCorsSupport.handleRequest(request, context);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST.getStatusCode());
        assertThat(response.getBody()).contains(String.format("Options method should include the %s header", ACCESS_CONTROL_REQUEST_METHOD.toLowerCase()));
    }

    @Test
    public void corsSupportShouldReturnBadRequestForNonRegisteredMethod() {
        LambdaProxyHandler<Configuration> handlerWithCORSSupport = new TestLambdaProxyHandler(true);
        String methodBeingInvestigated = "GET";
        Collection<String> supportedMethods = singletonList("POST");
        MediaType mediaType1 = MediaType.create("application", "type1");
        MediaType mediaType2 = MediaType.create("application", "type2");
        MediaType mediaType3 = MediaType.create("application", "type3");
        MediaType mediaType4 = MediaType.create("application", "type4");
        Collection<String> requiredHeaders = Stream.of("header1", "header2")
                .map(Util::randomizeCase)
                .collect(toList());
        SampleMethodHandler sampleMethodHandler = new SampleMethodHandler(requiredHeaders);
        sampleMethodHandler.registerPerAccept(mediaType1, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerAccept(mediaType2, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerAccept(mediaType3, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerContentType(mediaType4, mock(ContentTypeMapper.class));
        supportedMethods.forEach(method -> handlerWithCORSSupport.registerMethodHandler(
                method,
                c -> sampleMethodHandler
        ));
        Map<String, String> headers = new ConcurrentHashMap<>();
        headers.put(ACCESS_CONTROL_REQUEST_METHOD, methodBeingInvestigated);
        Collection<String> requestHeaders = new HashSet<>(requiredHeaders);
        requestHeaders.add("header3");
        headers.put(ACCESS_CONTROL_REQUEST_HEADERS, String.join(", ", requestHeaders));
        headers.put("Content-Type", mediaType4.toString());
        randomiseKeyValues(headers);
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod("OPTIONS")
                .withHeaders(headers)
                .withContext(context)
                .build();

        ApiGatewayProxyResponse response = handlerWithCORSSupport.handleRequest(request, context);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST.getStatusCode());
        assertThat(response.getBody()).isEqualTo(String.format("Lambda cannot handle the method %s", methodBeingInvestigated.toLowerCase()));
    }

    @Test
    public void corsSupportShouldReturnBadRequestWhenRequiredHeadersNotPresent() {
        LambdaProxyHandler<Configuration> handlerWithCORSSupport = new TestLambdaProxyHandler(true);
        String methodBeingInvestigated = "GET";
        Collection<String> supportedMethods = asList(methodBeingInvestigated, "POST");
        MediaType mediaType1 = MediaType.create("application", "type1");
        MediaType mediaType2 = MediaType.create("application", "type2");
        MediaType mediaType3 = MediaType.create("application", "type3");
        MediaType mediaType4 = MediaType.create("application", "type4");
        List<String> requiredHeaders = Stream.of("header1", "header2")
                .map(Util::randomizeCase)
                .collect(toList());
        SampleMethodHandler sampleMethodHandler = new SampleMethodHandler(requiredHeaders);
        sampleMethodHandler.registerPerAccept(mediaType1, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerAccept(mediaType2, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerAccept(mediaType3, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerContentType(mediaType4, mock(ContentTypeMapper.class));
        supportedMethods.forEach(method -> handlerWithCORSSupport.registerMethodHandler(
                method,
                c -> sampleMethodHandler
        ));
        Map<String, String> headers = new ConcurrentHashMap<>();
        headers.put(ACCESS_CONTROL_REQUEST_METHOD, methodBeingInvestigated);
        headers.put(ACCESS_CONTROL_REQUEST_HEADERS, "");
        headers.put("Content-Type", mediaType4.toString());
        randomiseKeyValues(headers);
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod("OPTIONS")
                .withHeaders(headers)
                .withContext(context)
                .build();

        ApiGatewayProxyResponse response = handlerWithCORSSupport.handleRequest(request, context);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST.getStatusCode());
        assertThat(response.getBody()).contains(String.format("The required header(s) not present: %s", String.join(", ", requiredHeaders.stream().map(String::toLowerCase).collect(toList()))));
    }

    @Test
    public void corsSupportShouldReturnAccessControlAllowOriginHeaderFromMethodHandler() throws Exception {
        LambdaProxyHandler<Configuration> handlerWithCORSSupport = new TestLambdaProxyHandler(true);
        String someHeader = "someHeader";
        String someValue = "someValue";
        Map<String, String> requestHeaders = new ConcurrentHashMap<>();
        requestHeaders.put(CONTENT_TYPE, CONTENT_TYPE_1.toString());
        requestHeaders.put(ACCEPT, ACCEPT_TYPE_1.toString());
        requestHeaders.put(someHeader, someValue);
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod(METHOD)
                .withHeaders(requestHeaders)
                .withContext(context)
                .build();
        Map<String, String> responseHeaders = new ConcurrentHashMap<>();
        responseHeaders.put(someHeader, someValue);
        String accessControlAllowOriginKey = "Access-Control-Allow-Origin";
        String accessControlAllowOriginValue = "*";
        when(methodHandler.handle(request, singletonList(CONTENT_TYPE_1), singletonList(ACCEPT_TYPE_1), context))
                .thenReturn(new ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder()
                        .withStatusCode(OK.getStatusCode())
                        .withHeaders(responseHeaders)
                        .build());
        handlerWithCORSSupport.registerMethodHandler(METHOD, c -> methodHandler);

        ApiGatewayProxyResponse response = handlerWithCORSSupport.handleRequest(request, context);

        assertThat(response).isNotNull();
        assertThat(response.getHeaders()).containsKey(accessControlAllowOriginKey);
        assertThat(response.getHeaders().get(accessControlAllowOriginKey)).isEqualTo(accessControlAllowOriginValue);
    }

    @Test
    public void mediaTypesAreCreatedWithLowerCaseValues() throws Exception {
        String someHeader = "someHeader";
        String someValue = "someValue";
        Map<String, String> requestHeaders = new ConcurrentHashMap<>();
        requestHeaders.put(CONTENT_TYPE, CONTENT_TYPE_1.toString());
        requestHeaders.put(ACCEPT, ACCEPT_TYPE_1.toString());
        requestHeaders.put(someHeader, someValue);
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod(METHOD)
                .withHeaders(requestHeaders)
                .withContext(context)
                .build();
        Map<String, String> responseHeaders = new ConcurrentHashMap<>();
        responseHeaders.put(someHeader, someValue);
        when(methodHandler.handle(request, singletonList(CONTENT_TYPE_1), singletonList(ACCEPT_TYPE_1), context))
                .thenReturn(new ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder()
                        .withStatusCode(OK.getStatusCode())
                        .withHeaders(responseHeaders)
                        .build());
        handler.registerMethodHandler(METHOD, c -> methodHandler);

        handler.handleRequest(request, context);

        verify(methodHandler).handle(any(), eq(singletonList(CONTENT_TYPE_1)), eq(singletonList(ACCEPT_TYPE_1)), any());
    }

    private class TestLambdaProxyHandler extends LambdaProxyHandler<Configuration> {

        public TestLambdaProxyHandler(boolean corsSupport) {
            super(corsSupport);
        }

        @Override
        protected Configuration getConfiguration(ApiGatewayProxyRequest request, Context context) {
            return configuration;
        }
    }

    private class TestLambdaProxyHandlerWithFailingConguration extends LambdaProxyHandler<Configuration> {

        public TestLambdaProxyHandlerWithFailingConguration() {
            super(false);
        }

        @Override
        protected Configuration getConfiguration(ApiGatewayProxyRequest request, Context context) {
            throw new NullPointerException();
        }
    }
}