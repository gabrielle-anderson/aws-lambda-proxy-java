package com.onelostlogician.aws.proxy;

import com.amazonaws.services.lambda.runtime.Context;

import java.util.Map;

import static java.util.Objects.requireNonNull;

public class ApiGatewayProxyRequest {
    private String resource;
    private String path;
    private String httpMethod;
    private Map<String, String> headers;
    private Map<String, String> queryStringParameters;
    private Map<String, String> pathParameters;
    private Map<String, String> stageVariables;
    private Context context;
    private String body;
    private Boolean isBase64Encoded;

    public ApiGatewayProxyRequest() {}

    public String getResource() {
        return resource;
    }

    public String getPath() {
        return path;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, String> getQueryStringParameters() {
        return queryStringParameters;
    }

    public Map<String, String> getPathParameters() {
        return pathParameters;
    }

    public Map<String, String> getStageVariables() {
        return stageVariables;
    }

    public Context getContext() {
        return context;
    }

    public String getBody() {
        return body;
    }

    public boolean getIsBase64Encoded() {
        return isBase64Encoded;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public void setQueryStringParameters(Map<String, String> queryStringParameters) {
        this.queryStringParameters = queryStringParameters;
    }

    public void setPathParameters(Map<String, String> pathParameters) {
        this.pathParameters = pathParameters;
    }

    public void setStageVariables(Map<String, String> stageVariables) {
        this.stageVariables = stageVariables;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public void setBase64Encoded(Boolean base64Encoded) {
        isBase64Encoded = base64Encoded;
    }

    public ApiGatewayProxyRequest(String resource, String path, String httpMethod, Map<String, String> headers, Map<String, String> queryStringParameters, Map<String, String> pathParameters, Map<String, String> stageVariables, Context context, String body, boolean isBase64Encoded) {
        this.resource = requireNonNull(resource);
        this.path = requireNonNull(path);
        this.httpMethod = requireNonNull(httpMethod);
        this.headers = requireNonNull(headers);
        this.queryStringParameters = requireNonNull(queryStringParameters);
        this.pathParameters = requireNonNull(pathParameters);
        this.stageVariables = requireNonNull(stageVariables);
        this.context = context;
        this.body = requireNonNull(body);
        this.isBase64Encoded = requireNonNull(isBase64Encoded);
    }

    @Override
    public String toString() {
        return "ApiGatewayProxyRequest{" +
                "resource='" + resource + '\'' +
                ", path='" + path + '\'' +
                ", httpMethod='" + httpMethod + '\'' +
                ", headers=" + headers +
                ", queryStringParameters=" + queryStringParameters +
                ", pathParameters=" + pathParameters +
                ", stageVariables=" + stageVariables +
                ", context=" + context +
                ", body='" + body + '\'' +
                ", isBase64Encoded=" + isBase64Encoded +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApiGatewayProxyRequest)) return false;

        ApiGatewayProxyRequest that = (ApiGatewayProxyRequest) o;

        if (isBase64Encoded != that.isBase64Encoded) return false;
        if (resource != null ? !resource.equals(that.resource) : that.resource != null) return false;
        if (path != null ? !path.equals(that.path) : that.path != null) return false;
        if (httpMethod != null ? !httpMethod.equals(that.httpMethod) : that.httpMethod != null) return false;
        if (headers != null ? !headers.equals(that.headers) : that.headers != null) return false;
        if (queryStringParameters != null ? !queryStringParameters.equals(that.queryStringParameters) : that.queryStringParameters != null)
            return false;
        if (pathParameters != null ? !pathParameters.equals(that.pathParameters) : that.pathParameters != null)
            return false;
        if (stageVariables != null ? !stageVariables.equals(that.stageVariables) : that.stageVariables != null)
            return false;
        if (context != null ? !context.equals(that.context) : that.context != null)
            return false;
        return body != null ? body.equals(that.body) : that.body == null;

    }

    @Override
    public int hashCode() {
        int result = resource != null ? resource.hashCode() : 0;
        result = 31 * result + (path != null ? path.hashCode() : 0);
        result = 31 * result + (httpMethod != null ? httpMethod.hashCode() : 0);
        result = 31 * result + (headers != null ? headers.hashCode() : 0);
        result = 31 * result + (queryStringParameters != null ? queryStringParameters.hashCode() : 0);
        result = 31 * result + (pathParameters != null ? pathParameters.hashCode() : 0);
        result = 31 * result + (stageVariables != null ? stageVariables.hashCode() : 0);
        result = 31 * result + (context != null ? context.hashCode() : 0);
        result = 31 * result + (body != null ? body.hashCode() : 0);
        result = 31 * result + (isBase64Encoded ? 1 : 0);
        return result;
    }
}
