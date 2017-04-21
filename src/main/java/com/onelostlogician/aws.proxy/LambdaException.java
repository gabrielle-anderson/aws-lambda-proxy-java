package com.onelostlogician.aws.proxy;

import java.util.Objects;

class LambdaException extends Exception {
    private final ApiGatewayProxyResponse response;

    LambdaException(ApiGatewayProxyResponse response) {
        Objects.requireNonNull(response);
        this.response = response;
    }

    ApiGatewayProxyResponse getResponse() {
        return response;
    }
}