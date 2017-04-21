package com.onelostlogician.aws.proxy.fixtures;

import com.amazonaws.services.lambda.runtime.LambdaLogger;

public class TestingLogger implements LambdaLogger {
    public void log(String string) {
        System.out.println(string);
    }
}
