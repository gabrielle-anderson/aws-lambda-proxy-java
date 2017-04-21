package com.onelostlogician.aws.proxy.fixtures;

import com.onelostlogician.aws.proxy.MethodHandler;

import java.util.Collection;

public class SampleMethodHandler extends MethodHandler<Integer, Integer> {
    public SampleMethodHandler(Collection<String> requiredHeaders) {
        super(requiredHeaders);
    }

    @Override
    public Integer handle(Integer integer) {
        return 0;
    }
}