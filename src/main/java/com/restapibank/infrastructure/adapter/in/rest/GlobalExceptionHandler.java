package com.restapibank.infrastructure.adapter.in.rest;

import com.restapibank.domain.exception.CountryNotFoundException;
import com.restapibank.domain.exception.ExternalServiceException;
import com.restapibank.domain.exception.InvalidCountryCodeException;
import com.restapibank.infrastructure.adapter.in.rest.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorResponseFactory errors;

    public GlobalExceptionHandler(ErrorResponseFactory errors) {
        this.errors = errors;
    }

    @ExceptionHandler(InvalidCountryCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCode(InvalidCountryCodeException e, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e,
                                                                HttpServletRequest request) {
        String message = e.getParameterValidationResults().stream()
                .findFirst()
                .map(result -> invalidParameter(result.getMethodParameter().getParameterName(), result.getArgument()))
                .orElse("La solicitud contiene parámetros inválidos.");
        return respond(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e,
                                                            HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, invalidParameter(e.getName(), e.getValue()), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e,
                                                                   HttpServletRequest request) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> {
                    String parameter = null;
                    for (Path.Node node : violation.getPropertyPath()) {
                        parameter = node.getName();
                    }
                    return invalidParameter(parameter, violation.getInvalidValue());
                })
                .orElse("La solicitud contiene parámetros inválidos.");
        return respond(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "La solicitud contiene parámetros inválidos.", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e,
                                                                HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST,
                "El parámetro '" + e.getParameterName() + "' es obligatorio.", request);
    }

    @ExceptionHandler(CountryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCountryNotFound(CountryNotFoundException e, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "El recurso solicitado no existe.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e,
                                                                  HttpServletRequest request) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED,
                "El método " + e.getMethod() + " no está permitido para este recurso.", request);
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ErrorResponse> handleExternalService(ExternalServiceException e, HttpServletRequest request) {
        log.warn("Falla al consumir el Banco Mundial: {}", e.getMessage(), e);
        return respond(HttpStatus.BAD_GATEWAY,
                "No fue posible obtener la información del Banco Mundial. Intente nuevamente más tarde.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Error inesperado en {} {}", request.getMethod(), request.getRequestURI(), e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno inesperado.", request);
    }

    private static String invalidParameter(String name, Object value) {
        return switch (String.valueOf(name)) {
            case "code" -> "El código de país '" + value + "' no es válido: debe tener 2 o 3 letras.";
            case "id" -> "El id '" + value + "' no es válido: debe ser un número entero positivo.";
            default -> "El parámetro '" + name + "' tiene un valor inválido: '" + value + "'.";
        };
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(errors.create(status, message, request.getRequestURI()));
    }
}
