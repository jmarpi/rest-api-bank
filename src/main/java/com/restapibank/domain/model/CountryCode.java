package com.restapibank.domain.model;

import com.restapibank.domain.exception.InvalidCountryCodeException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Código de país recibido por la API: ISO alpha-2 (2 letras) o alpha-3 (3 letras),
 * normalizado a mayúsculas y sin espacios alrededor.
 */
public record CountryCode(String value) {

    private static final Pattern VALID = Pattern.compile("^[A-Z]{2,3}$");

    public CountryCode {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new InvalidCountryCodeException(value);
        }
    }

    public static CountryCode of(String raw) {
        if (raw == null) {
            throw new InvalidCountryCodeException(null);
        }
        return new CountryCode(raw.strip().toUpperCase(Locale.ROOT));
    }

    public boolean isAlpha2() {
        return value.length() == 2;
    }

    public boolean isAlpha3() {
        return value.length() == 3;
    }
}
