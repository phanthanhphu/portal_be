package org.bsl.portal.exception;

import org.springframework.http.HttpStatus;

public class LoginFailureException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public LoginFailureException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public LoginFailureException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
