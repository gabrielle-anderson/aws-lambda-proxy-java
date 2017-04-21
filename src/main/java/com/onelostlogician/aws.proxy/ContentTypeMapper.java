package com.onelostlogician.aws.proxy;

import com.amazonaws.services.lambda.runtime.Context;

public interface ContentTypeMapper<Input> {
    Input toInput(ApiGatewayProxyRequest request, Context context) throws Exception;
}
