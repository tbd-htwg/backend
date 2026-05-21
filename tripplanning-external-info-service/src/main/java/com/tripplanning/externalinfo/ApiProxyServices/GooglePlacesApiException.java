package com.tripplanning.externalinfo.ApiProxyServices;

public class GooglePlacesApiException extends RuntimeException {

    public GooglePlacesApiException(String message) {
        super(message);
    }

    public GooglePlacesApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
