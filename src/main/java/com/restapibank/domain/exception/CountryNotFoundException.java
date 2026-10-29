package com.restapibank.domain.exception;

public class CountryNotFoundException extends RuntimeException {

    public CountryNotFoundException(String code) {
        super("No se encontró el país con código '" + code + "'.");
    }

    private CountryNotFoundException(long id) {
        super("No se encontró el país con id '" + id + "'.");
    }

    public static CountryNotFoundException byId(long id) {
        return new CountryNotFoundException(id);
    }
}
