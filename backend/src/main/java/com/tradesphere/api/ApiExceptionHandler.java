package com.tradesphere.api;

import java.util.LinkedHashMap;
import com.tradesphere.catalog.ProductConflictException;
import com.tradesphere.catalog.ProductNotFoundException;
import com.tradesphere.inventory.InventoryException;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InventoryException.class)
    ProblemDetail inventory(InventoryException exception) {
        return problem(exception.status(), exception.status().getReasonPhrase(), exception.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    ProblemDetail notFound(ProductNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Product not found", exception.getMessage());
    }

    @ExceptionHandler(ProductConflictException.class)
    ProblemDetail conflict(ProductConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Product conflict", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidFields(MethodArgumentNotValidException exception) {
        var errors = new LinkedHashMap<String, String>();
        exception.getBindingResult().getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        var problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Check the highlighted fields.");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail invalidParameters(ConstraintViolationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid parameters", "Check search/filter lengths and use page >= 0 and size between 1 and 100.");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail invalidRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Check the JSON body, identifier, and parameter types.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        LOG.error("Unhandled API error of type {}", exception.getClass().getSimpleName());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", "The request could not be completed. Please try again.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        var result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setTitle(title);
        return result;
    }
}
