package com.restapibank.infrastructure.security;

import com.restapibank.infrastructure.adapter.in.rest.ErrorResponseFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Exige {@code Authorization: Bearer <token>} con el token configurado en las rutas {@code /api/**}. */
public class BearerTokenFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final byte[] expectedToken;
    private final ErrorResponseFactory errors;

    public BearerTokenFilter(String expectedToken, ErrorResponseFactory errors) {
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isAuthorized(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            errors.write(response, HttpStatus.FORBIDDEN, "Token de acceso ausente o inválido.", request.getRequestURI());
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAuthorized(String header) {
        if (header == null || !header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return false;
        }
        byte[] provided = header.substring(PREFIX.length()).strip().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(provided, expectedToken);
    }
}
