package com.restapibank.infrastructure.adapter.in.rest;

import com.restapibank.infrastructure.adapter.in.rest.model.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

/** Construye el cuerpo de error uniforme de la API. */
@Component
public class ErrorResponseFactory {

    private final JsonMapper jsonMapper;

    public ErrorResponseFactory(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public ErrorResponse create(HttpStatus status, String message, String path) {
        return new ErrorResponse(status.value(), status.getReasonPhrase(), message, OffsetDateTime.now(), path);
    }

    /** Escribe el error directamente en la respuesta; usado desde filtros, fuera de los controladores. */
    public void write(HttpServletResponse response, HttpStatus status, String message, String path) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), create(status, message, path));
    }
}
