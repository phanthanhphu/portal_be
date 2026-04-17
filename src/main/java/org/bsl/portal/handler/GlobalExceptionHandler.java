package org.bsl.portal.handler;

import org.bsl.portal.error.ApiError;
import org.bsl.portal.exception.DuplicateSupplierProductException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DuplicateSupplierProductException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateSupplierProductException ex) {
        logger.error("Duplicate entry: {}", ex.getMessage());
        ApiError error = new ApiError(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                List.of("Duplicate supplierCode, sapCode, price")
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArg(IllegalArgumentException ex) {
        logger.error("Invalid argument: {}", ex.getMessage());
        ApiError error = new ApiError(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                null
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAllExceptions(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage());
        ApiError error = new ApiError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error occurred",
                null
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
