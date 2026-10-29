package com.restapibank.application.service;

import com.restapibank.application.port.in.QueryCountriesUseCase;
import com.restapibank.application.port.out.CountryRepositoryPort;
import com.restapibank.domain.exception.CountryNotFoundException;
import com.restapibank.domain.model.Country;

import java.util.List;

/** Consultas sobre los países almacenados; nunca llaman al proveedor externo. */
public class QueryCountriesService implements QueryCountriesUseCase {

    private final CountryRepositoryPort repository;

    public QueryCountriesService(CountryRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public List<Country> findAll() {
        return repository.findAll();
    }

    @Override
    public Country findById(long id) {
        return repository.findById(id).orElseThrow(() -> CountryNotFoundException.byId(id));
    }
}
