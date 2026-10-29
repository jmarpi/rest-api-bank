package com.restapibank.application.port.out;

import com.restapibank.domain.model.Country;

import java.util.List;
import java.util.Optional;

public interface CountryRepositoryPort {

    Optional<Country> findById(long id);

    Optional<Country> findByAlpha2(String alpha2Code);

    Optional<Country> findByAlpha3(String alpha3Code);

    List<Country> findAll();

    /** Inserta el país o, si ya existe uno con el mismo alpha-2, actualiza sus datos (conserva su id). */
    Country save(Country country);
}
