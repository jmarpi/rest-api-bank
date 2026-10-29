package com.restapibank.domain.exception;

public class InvalidCountryCodeException extends RuntimeException {

    public InvalidCountryCodeException(String code) {
        super(code == null || code.isBlank()
                ? "El código de país es obligatorio."
                : "El código de país '" + code + "' no es válido: debe tener 2 o 3 letras.");
    }
}
