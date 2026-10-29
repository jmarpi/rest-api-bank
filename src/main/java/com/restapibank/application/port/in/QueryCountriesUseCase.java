package com.restapibank.application.port.in;

import com.restapibank.domain.model.Country;

import java.util.List;

public interface QueryCountriesUseCase {

    List<Country> findAll();

    Country findById(long id);
}
