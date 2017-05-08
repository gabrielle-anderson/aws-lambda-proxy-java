package com.onelostlogician.aws.proxy;

import java.util.Map;
import java.util.Random;

public class Util {
    public static String randomizeCase(String str) {
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(str.length());

        for (char c : str.toCharArray())
            sb.append(rnd.nextBoolean()
                    ? Character.toLowerCase(c)
                    : Character.toUpperCase(c));

        return sb.toString();
    }

    public static void randomiseKeyValues(Map<String, String> headers) {
        headers.forEach((key, value) -> {
            headers.remove(key);
            headers.put(randomizeCase(key), randomizeCase(value));
        });
    }
}
