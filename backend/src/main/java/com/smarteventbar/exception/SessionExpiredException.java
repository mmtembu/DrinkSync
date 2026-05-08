package com.smarteventbar.exception;

public class SessionExpiredException extends RuntimeException {

    public SessionExpiredException(String sessionId) {
        super("Session expired: " + sessionId);
    }
}
