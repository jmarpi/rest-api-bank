package com.restapibank.domain.model;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * País importado desde el Banco Mundial. Su identidad de negocio es el código ISO alpha-2;
 * {@code id} es el identificador asignado al almacenarlo ({@code null} si aún no se almacenó).
 * Los textos se recortan y los vacíos se normalizan a {@code null}.
 */
public record Country(
        Long id,
        String alpha2Code,
        String alpha3Code,
        String name,
        String capital,
        String region,
        String incomeLevel,
        BigDecimal latitude,
        BigDecimal longitude) {

    public Country {
        alpha2Code = upper(clean(alpha2Code));
        if (alpha2Code == null) {
            throw new IllegalArgumentException("El código ISO alpha-2 del país es obligatorio.");
        }
        alpha3Code = upper(clean(alpha3Code));
        name = clean(name);
        capital = clean(capital);
        region = clean(region);
        incomeLevel = clean(incomeLevel);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
