package com.restapibank.application.port.in;

import com.restapibank.domain.model.Country;

/** Resultado de una importación: el país almacenado y si fue creado ({@code true}) o ya existía. */
public record ImportResult(Country country, boolean created) {
}
