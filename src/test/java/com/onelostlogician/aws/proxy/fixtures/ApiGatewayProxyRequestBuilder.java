package com.onelostlogician.aws.proxy.fixtures;

import com.amazonaws.services.lambda.runtime.Context;
import com.onelostlogician.aws.proxy.ApiGatewayProxyRequest;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class ApiGatewayProxyRequestBuilder {
    private String resource = "";
    private String path = "";
    private String httpMethod = "";
    private Map<String, String> headers = new HashMap<>();
    private Map<String, String> queryStringParameters = new HashMap<>();
    private Map<String, String> pathParameters = new HashMap<>();
    private Map<String, String> stageVariables = new HashMap<>();
    private Context context;
    private String body = "";
    private boolean isBase64Encoded = false;

    public ApiGatewayProxyRequestBuilder withResource(String resource) {
        this.resource = requireNonNull(resource);
        return this;
    }

    public ApiGatewayProxyRequestBuilder withPath(String path) {
        this.path = requireNonNull(path);
        return this;
    }

    public ApiGatewayProxyRequestBuilder withHttpMethod(String httpMethod) {
        this.httpMethod = requireNonNull(httpMethod);
        return this;
    }

    public ApiGatewayProxyRequestBuilder withHeaders(Map<String, String> headers) {
        this.headers = requireNonNull(headers);
        return this;
    }

    public ApiGatewayProxyRequestBuilder withQueryStringParameters(Map<String, String> queryStringParameters) {
        this.queryStringParameters = requireNonNull(queryStringParameters);
        return this;
    }

    public ApiGatewayProxyRequestBuilder withPathParameters(Map<String, String> pathParameters) {
        this.pathParameters = requireNonNull(pathParameters);
        return this;
    }

    public ApiGatewayProxyRequestBuilder withStageVariables(Map<String, String> stageVariables) {
        this.stageVariables = requireNonNull(stageVariables);
        return this;
    }

    public ApiGatewayProxyRequestBuilder withContext(Context requestContext) {
        this.context = requestContext;
        return this;
    }

    public ApiGatewayProxyRequestBuilder withBody(String body) {
        this.body = requireNonNull(body);
        return this;
    }

    public ApiGatewayProxyRequestBuilder withIsBase64Encoded(boolean isBase64Encoded) {
        this.isBase64Encoded = requireNonNull(isBase64Encoded);
        return this;
    }

    public ApiGatewayProxyRequest build() {
        return new ApiGatewayProxyRequest(resource, path, httpMethod, headers, queryStringParameters, pathParameters, stageVariables, context, body, isBase64Encoded);
    }
}
