package com.onelostlogician.aws.proxy;

public interface AcceptMapper<Output> {
    ApiGatewayProxyResponse outputToResponse(Output output) throws Exception;
}
