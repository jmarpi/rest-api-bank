package com.restapibank.infrastructure.adapter.out.worldbank;

import com.restapibank.application.port.out.CountryProviderPort;
import com.restapibank.domain.exception.ExternalServiceException;
import com.restapibank.domain.model.Country;
import com.restapibank.domain.model.CountryCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Consulta {@code GET {baseUrl}/country/{code}?format=json}. La respuesta es un arreglo
 * heterogéneo: {@code [ {paginación}, [países] ]} o {@code [ {"message": [...]} ]}.
 */
@Component
public class WorldBankCountryAdapter implements CountryProviderPort {

    private final RestClient restClient;

    public WorldBankCountryAdapter(@Qualifier("worldBankRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<Country> fetchByCode(CountryCode code) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri("/country/{code}?format=json", code.value())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Error al consultar el Banco Mundial para '" + code.value() + "'", e);
        }
        return interpret(body, code);
    }

    private Optional<Country> interpret(JsonNode body, CountryCode code) {
        if (body == null || !body.isArray() || body.isEmpty()) {
            throw unexpected(code);
        }
        if (body.get(0).has("message")) {
            return Optional.empty();
        }
        if (body.size() < 2) {
            throw unexpected(code);
        }
        JsonNode countries = body.get(1);
        if (countries == null || countries.isNull() || (countries.isArray() && countries.isEmpty())) {
            return Optional.empty();
        }
        if (!countries.isArray() || !countries.get(0).isObject()) {
            throw unexpected(code);
        }
        return Optional.of(toCountry(countries.get(0), code));
    }

    private Country toCountry(JsonNode node, CountryCode code) {
        String alpha2 = text(node.get("iso2Code"));
        if (alpha2 == null || alpha2.isBlank()) {
            throw new ExternalServiceException("El Banco Mundial no devolvió el código alpha-2 para '" + code.value() + "'");
        }
        return new Country(
                null,
                alpha2,
                text(node.get("id")),
                text(node.get("name")),
                text(node.get("capitalCity")),
                text(node.path("region").get("value")),
                text(node.path("incomeLevel").get("value")),
                decimal(node.get("latitude")),
                decimal(node.get("longitude")));
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() || !node.isValueNode() ? null : node.asString();
    }

    private static BigDecimal decimal(JsonNode node) {
        String value = text(node);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ExternalServiceException unexpected(CountryCode code) {
        return new ExternalServiceException("Respuesta inesperada del Banco Mundial para '" + code.value() + "'");
    }
}
