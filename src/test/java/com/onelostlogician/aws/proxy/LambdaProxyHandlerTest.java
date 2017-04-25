package com.onelostlogician.aws.proxy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
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

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static javax.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(ParallelParameterized.class)
public class LambdaProxyHandlerTest {
    private static final MediaType METHOD_CONTENT_TYPE = APPLICATION_JSON_TYPE;
    private static final MediaType METHOD_ACCEPT = TEXT_PLAIN_TYPE;
    private static final String METHOD = "GET";
    private static final String ACCESS_CONTROL_REQUEST_METHOD = "Access-Control-Request-Method";
    private static final String ACCESS_CONTROL_REQUEST_HEADERS = "Access-Control-Request-Headers";

    private final LambdaProxyHandler<Configuration> handler;

    private Configuration configuration = mock(Configuration.class);
    private Context context = mock(Context.class);
    private LambdaLogger logger = new TestingLogger();
    private MethodHandler methodHandler = mock(MethodHandler.class);
    private Function<Configuration, MethodHandler> constructor = c -> methodHandler;

    public LambdaProxyHandlerTest(boolean optionsSupport) {
        handler = new TestLambdaProxyHandler(optionsSupport);
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
                .withHeaders(new HashMap<>())
                .withContext(context)
                .build();
        ApiGatewayProxyResponse response = handler.handleRequest(request, context);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST.getStatusCode());
        assertThat(response.getBody()).isEqualTo(String.format("Lambda cannot handle the method %s", METHOD));
    }

    @Test
    public void shouldReturnServerErrorAndReasonWhenMisconfigured() throws ParseException {
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod(METHOD)
                .build();
        LambdaProxyHandler<Configuration> handlerWithFailingConguration = new TestLambdaProxyHandlerWithFailingConguration();
        handlerWithFailingConguration.registerMethodHandler(METHOD, constructor);

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
                .withHeaders(new HashMap<>())
                .withContext(context)
                .build();
        handler.registerMethodHandler(METHOD, constructor);

        ApiGatewayProxyResponse response = handler.handleRequest(request, context);

        assertThat(response.getStatusCode()).isEqualTo(UNSUPPORTED_MEDIA_TYPE.getStatusCode());
        assertThat(response.getBody()).isEqualTo(String.format("No %s header", CONTENT_TYPE));
    }

    @Test
    public void shouldReturnUnsupportedMediaTypeIfAcceptNotSpecified() throws IOException, ParseException {
        Map<String, String> headers = new HashMap<>();
        headers.put(CONTENT_TYPE, METHOD_CONTENT_TYPE.toString());
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod(METHOD)
                .withHeaders(headers)
                .withContext(context)
                .build();
        handler.registerMethodHandler(METHOD, constructor);

        ApiGatewayProxyResponse response = handler.handleRequest(request, context);

        assertThat(response.getStatusCode()).isEqualTo(UNSUPPORTED_MEDIA_TYPE.getStatusCode());
        assertThat(response.getBody()).isEqualTo(String.format("No %s header", ACCEPT));
    }

    @Test
    public void shouldReturnBadRequestForMalformedMediaTypes() throws Exception {
        String someHeader = "someHeader";
        String someValue = "someValue";
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put(CONTENT_TYPE, "vnd.ms-excel");
        requestHeaders.put(ACCEPT, METHOD_ACCEPT.toString());
        requestHeaders.put(someHeader, someValue);
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod(METHOD)
                .withHeaders(requestHeaders)
                .withContext(context)
                .build();
        HashMap<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put(someHeader, someValue);
        when(methodHandler.handle(request, METHOD_CONTENT_TYPE, METHOD_ACCEPT, context))
                .thenReturn(
                        new ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder()
                                .withStatusCode(OK.getStatusCode())
                                .withHeaders(responseHeaders)
                                .build()
                );
        handler.registerMethodHandler(METHOD, constructor);

        ApiGatewayProxyResponse response = handler.handleRequest(request, context);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST.getStatusCode());
        assertThat(response.getBody()).contains("Malformed media type");
    }

    @Test
    public void shouldReturnResponseFromMethodHandler() throws Exception {
        String someHeader = "someHeader";
        String someValue = "someValue";
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put(CONTENT_TYPE, METHOD_CONTENT_TYPE.toString());
        requestHeaders.put(ACCEPT, METHOD_ACCEPT.toString());
        requestHeaders.put(someHeader, someValue);
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod(METHOD)
                .withHeaders(requestHeaders)
                .withContext(context)
                .build();
        HashMap<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put(someHeader, someValue);
        when(methodHandler.handle(request, METHOD_CONTENT_TYPE, METHOD_ACCEPT, context))
                .thenReturn(new ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder()
                        .withStatusCode(OK.getStatusCode())
                        .withHeaders(responseHeaders)
                        .build());
        handler.registerMethodHandler(METHOD, constructor);

        ApiGatewayProxyResponse response = handler.handleRequest(request, context);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(OK.getStatusCode());
        assertThat(response.getBody()).isEqualTo("");
        assertThat(response.getHeaders()).isEqualTo(responseHeaders);
    }

    @Test
    public void shouldReturnResponseFromMethodHandlerWithDifferentlyCasedContentTypeAndAccept() throws Exception {
        String someHeader = "someHeader";
        String someValue = "someValue";
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put(CONTENT_TYPE, METHOD_CONTENT_TYPE.toString().toUpperCase());
        requestHeaders.put(ACCEPT, METHOD_ACCEPT.toString().toUpperCase());
        requestHeaders.put(someHeader, someValue);
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod(METHOD)
                .withHeaders(requestHeaders)
                .withContext(context)
                .build();
        HashMap<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put(someHeader, someValue);
        when(methodHandler.handle(request, METHOD_CONTENT_TYPE, METHOD_ACCEPT, context))
                .thenReturn(new ApiGatewayProxyResponse.ApiGatewayProxyResponseBuilder()
                        .withStatusCode(OK.getStatusCode())
                        .withHeaders(responseHeaders)
                        .build());
        handler.registerMethodHandler(METHOD, constructor);

        ApiGatewayProxyResponse response = handler.handleRequest(request, context);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(OK.getStatusCode());
        assertThat(response.getBody()).isEqualTo("");
        assertThat(response.getHeaders()).isEqualTo(responseHeaders);
    }

    @Test
    public void shouldPassThroughErrorMessageFromMethodHandlerInvocationIfDebug() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put(CONTENT_TYPE, METHOD_CONTENT_TYPE.toString());
        headers.put(ACCEPT, METHOD_ACCEPT.toString());
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
        when(methodHandler.handle(request, METHOD_CONTENT_TYPE, METHOD_ACCEPT, context))
                .thenThrow(new RuntimeException(message, cause));
        handler.registerMethodHandler(METHOD, constructor);

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
    public void optionsSupportShouldReturnOkForRegisteredMethodAndMediaTypes() {
        LambdaProxyHandler<Configuration> handlerWithOptionsSupport = new TestLambdaProxyHandler(true);
        String methodBeingInvestigated = "GET";
        Collection<String> supportedMethods = asList(methodBeingInvestigated, "POST");
        MediaType mediaType1 = new MediaType("application", "type1");
        MediaType mediaType2 = new MediaType("application", "type2");
        MediaType mediaType3 = new MediaType("application", "type3");
        MediaType mediaType4 = new MediaType("application", "type4");
        Collection<String> requiredHeaders = asList("header1", "header2");
        SampleMethodHandler sampleMethodHandler = new SampleMethodHandler(requiredHeaders);
        sampleMethodHandler.registerPerAccept(mediaType1, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerAccept(mediaType2, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerAccept(mediaType3, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerContentType(mediaType4, mock(ContentTypeMapper.class));
        supportedMethods.forEach(method -> handlerWithOptionsSupport.registerMethodHandler(
                method,
                c -> sampleMethodHandler
        ));
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Request-Method", methodBeingInvestigated);
        Collection<String> requestHeaders = new HashSet<>(requiredHeaders);
        requestHeaders.add("header3");
        headers.put("Access-Control-Request-Headers", String.join(", ", requestHeaders));

        headers.put("Content-Type", mediaType4.toString());
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod("OPTIONS")
                .withHeaders(headers)
                .withContext(context)
                .build();

        ApiGatewayProxyResponse response = handlerWithOptionsSupport.handleRequest(request, context);

        assertThat(response.getStatusCode()).isEqualTo(OK.getStatusCode());
    }

    @Test
    public void optionsSupportShouldReturnBadRequestWhenRequestDoesNotSpecifyMethod() {
        LambdaProxyHandler<Configuration> handlerWithOptionsSupport = new TestLambdaProxyHandler(true);
        Collection<String> supportedMethods = singletonList("POST");
        MediaType mediaType1 = new MediaType("application", "type1");
        MediaType mediaType2 = new MediaType("application", "type2");
        MediaType mediaType3 = new MediaType("application", "type3");
        MediaType mediaType4 = new MediaType("application", "type4");
        Collection<String> requiredHeaders = asList("header1", "header2");
        SampleMethodHandler sampleMethodHandler = new SampleMethodHandler(requiredHeaders);
        sampleMethodHandler.registerPerAccept(mediaType1, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerAccept(mediaType2, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerAccept(mediaType3, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerContentType(mediaType4, mock(ContentTypeMapper.class));
        supportedMethods.forEach(method -> handlerWithOptionsSupport.registerMethodHandler(
                method,
                c -> sampleMethodHandler
        ));
        Map<String, String> headers = new HashMap<>();
        Collection<String> requestHeaders = new HashSet<>(requiredHeaders);
        requestHeaders.add("header3");
        headers.put(ACCESS_CONTROL_REQUEST_HEADERS, String.join(", ", requestHeaders));

        headers.put("Content-Type", mediaType4.toString());
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod("OPTIONS")
                .withHeaders(headers)
                .withContext(context)
                .build();

        ApiGatewayProxyResponse response = handlerWithOptionsSupport.handleRequest(request, context);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST.getStatusCode());
        assertThat(response.getBody()).contains(String.format("Options method should include the %s header", ACCESS_CONTROL_REQUEST_METHOD));
    }

    @Test
    public void optionsSupportShouldReturnBadRequestForNonRegisteredMethod() {
        LambdaProxyHandler<Configuration> handlerWithOptionsSupport = new TestLambdaProxyHandler(true);
        String methodBeingInvestigated = "GET";
        Collection<String> supportedMethods = singletonList("POST");
        MediaType mediaType1 = new MediaType("application", "type1");
        MediaType mediaType2 = new MediaType("application", "type2");
        MediaType mediaType3 = new MediaType("application", "type3");
        MediaType mediaType4 = new MediaType("application", "type4");
        Collection<String> requiredHeaders = asList("header1", "header2");
        SampleMethodHandler sampleMethodHandler = new SampleMethodHandler(requiredHeaders);
        sampleMethodHandler.registerPerAccept(mediaType1, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerAccept(mediaType2, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerAccept(mediaType3, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerContentType(mediaType4, mock(ContentTypeMapper.class));
        supportedMethods.forEach(method -> handlerWithOptionsSupport.registerMethodHandler(
                method,
                c -> sampleMethodHandler
        ));
        Map<String, String> headers = new HashMap<>();
        headers.put(ACCESS_CONTROL_REQUEST_METHOD, methodBeingInvestigated);
        Collection<String> requestHeaders = new HashSet<>(requiredHeaders);
        requestHeaders.add("header3");
        headers.put(ACCESS_CONTROL_REQUEST_HEADERS, String.join(", ", requestHeaders));

        headers.put("Content-Type", mediaType4.toString());
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod("OPTIONS")
                .withHeaders(headers)
                .withContext(context)
                .build();

        ApiGatewayProxyResponse response = handlerWithOptionsSupport.handleRequest(request, context);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST.getStatusCode());
        assertThat(response.getBody()).isEqualTo(String.format("Lambda cannot handle the method %s", methodBeingInvestigated));
    }

    @Test
    public void optionsSupportShouldReturnBadRequestWhenRequiredHeadersNotPresent() {
        LambdaProxyHandler<Configuration> handlerWithOptionsSupport = new TestLambdaProxyHandler(true);
        String methodBeingInvestigated = "GET";
        Collection<String> supportedMethods = asList(methodBeingInvestigated, "POST");
        MediaType mediaType1 = new MediaType("application", "type1");
        MediaType mediaType2 = new MediaType("application", "type2");
        MediaType mediaType3 = new MediaType("application", "type3");
        MediaType mediaType4 = new MediaType("application", "type4");
        List<String> requiredHeaders = asList("header1", "header2");
        SampleMethodHandler sampleMethodHandler = new SampleMethodHandler(requiredHeaders);
        sampleMethodHandler.registerPerAccept(mediaType1, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerAccept(mediaType2, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerAccept(mediaType3, mock(AcceptMapper.class));
        sampleMethodHandler.registerPerContentType(mediaType4, mock(ContentTypeMapper.class));
        supportedMethods.forEach(method -> handlerWithOptionsSupport.registerMethodHandler(
                method,
                c -> sampleMethodHandler
        ));
        Map<String, String> headers = new HashMap<>();
        headers.put(ACCESS_CONTROL_REQUEST_METHOD, methodBeingInvestigated);
        headers.put(ACCESS_CONTROL_REQUEST_HEADERS, "");

        headers.put("Content-Type", mediaType4.toString());
        ApiGatewayProxyRequest request = new ApiGatewayProxyRequestBuilder()
                .withHttpMethod("OPTIONS")
                .withHeaders(headers)
                .withContext(context)
                .build();

        ApiGatewayProxyResponse response = handlerWithOptionsSupport.handleRequest(request, context);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST.getStatusCode());
        assertThat(response.getBody()).contains(String.format("The required header(s) not present: %s", String.join(", ", requiredHeaders)));
    }

    private class TestLambdaProxyHandler extends LambdaProxyHandler<Configuration> {

        public TestLambdaProxyHandler(boolean optionsSupport) {
            super(optionsSupport);
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