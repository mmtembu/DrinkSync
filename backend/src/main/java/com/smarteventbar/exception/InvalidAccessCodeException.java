package com.smarteventbar.exception;

public class InvalidAccessCodeException extends RuntimeException {

    public InvalidAccessCodeException() {
        super("Invalid access code");
    }
}
