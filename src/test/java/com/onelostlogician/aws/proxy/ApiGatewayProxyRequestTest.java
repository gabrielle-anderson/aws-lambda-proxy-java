package com.onelostlogician.aws.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onelostlogician.aws.proxy.fixtures.ApiGatewayProxyRequestBuilder;
import org.json.simple.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

public class ApiGatewayProxyRequestTest {
    @Test
    public void shouldDeseralise() throws IOException {
        ApiGatewayProxyRequest expected = new ApiGatewayProxyRequestBuilder()
                .withResource("/hello")
                .withPath("/world")
                .withHttpMethod("GET")
                .withHeaders(new HashMap<>())
                .withQueryStringParameters(new HashMap<>())
                .withPathParameters(new HashMap<>())
                .withStageVariables(new HashMap<>())
                .withBody("great")
                .withIsBase64Encoded(false)
                .build();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("resource", expected.getResource());
        jsonObject.put("path", expected.getPath());
        jsonObject.put("httpMethod", expected.getHttpMethod());
        jsonObject.put("headers", expected.getHeaders());
        jsonObject.put("queryStringParameters", expected.getQueryStringParameters());
        jsonObject.put("pathParameters", expected.getPathParameters());
        jsonObject.put("stageVariables", expected.getStageVariables());
        jsonObject.put("context", expected.getContext());
        jsonObject.put("body", expected.getBody());
        jsonObject.put("isBase64Encoded", expected.getIsBase64Encoded());

        ApiGatewayProxyRequest actual = new ObjectMapper().readValue(jsonObject.toJSONString(), ApiGatewayProxyRequest.class);
        assertThat(actual).isEqualTo(expected);
    }
}