package com.onelostlogician.aws.proxy;

import javax.ws.rs.core.MediaType;
import java.util.Map;

public class ParameterisedMediaType {
    private final MediaType mediaType;
    private final Map<String, String> parameters;

    public ParameterisedMediaType(MediaType mediaType, Map<String, String> parameters) {
        this.mediaType = mediaType;
        this.parameters = parameters;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public String getParameter(String key) {
        return parameters.get(key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ParameterisedMediaType that = (ParameterisedMediaType) o;

        if (mediaType != null ? !mediaType.equals(that.mediaType) : that.mediaType != null) return false;
        return parameters != null ? parameters.equals(that.parameters) : that.parameters == null;
    }

    @Override
    public int hashCode() {
        int result = mediaType != null ? mediaType.hashCode() : 0;
        result = 31 * result + (parameters != null ? parameters.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return mediaType.toString();
    }
}
