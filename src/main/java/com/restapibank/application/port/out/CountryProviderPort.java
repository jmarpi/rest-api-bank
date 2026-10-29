package com.restapibank.application.port.out;

import com.restapibank.domain.exception.ExternalServiceException;
import com.restapibank.domain.model.Country;
import com.restapibank.domain.model.CountryCode;

import java.util.Optional;

/** Fuente externa de países (Banco Mundial). */
public interface CountryProviderPort {

    /**
     * @return el país, o vacío si la fuente indica que el código no existe
     * @throws ExternalServiceException si la fuente no está disponible o su respuesta no es interpretable
     */
    Optional<Country> fetchByCode(CountryCode code);
}
