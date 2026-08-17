package com.arshat.livescore.web;

public class RequestTooLargeException extends RuntimeException {
    public RequestTooLargeException(String message) {
        super(message);
    }
}
