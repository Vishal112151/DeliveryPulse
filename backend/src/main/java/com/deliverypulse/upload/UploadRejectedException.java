package com.deliverypulse.upload;

import org.springframework.http.HttpStatus;

public class UploadRejectedException extends RuntimeException {

    private final HttpStatus status;

    public UploadRejectedException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public UploadRejectedException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public HttpStatus getStatus() {
        return status;
    }
}