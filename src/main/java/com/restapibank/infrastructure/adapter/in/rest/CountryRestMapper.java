package com.restapibank.infrastructure.adapter.in.rest;

import com.restapibank.domain.model.Country;
import org.springframework.stereotype.Component;

import java.util.List;

/** Traduce el modelo de dominio al DTO generado desde el contrato OpenAPI. */
@Component
public class CountryRestMapper {

    public com.restapibank.infrastructure.adapter.in.rest.model.Country toDto(Country country) {
        return new com.restapibank.infrastructure.adapter.in.rest.model.Country(country.id(), country.alpha2Code())
                .alpha3Code(country.alpha3Code())
                .name(country.name())
                .capital(country.capital())
                .region(country.region())
                .incomeLevel(country.incomeLevel())
                .latitude(country.latitude())
                .longitude(country.longitude());
    }

    public List<com.restapibank.infrastructure.adapter.in.rest.model.Country> toDtoList(List<Country> countries) {
        return countries.stream().map(this::toDto).toList();
    }
}
