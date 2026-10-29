package com.restapibank.infrastructure.adapter.in.rest;

import com.restapibank.application.port.in.ImportCountryUseCase;
import com.restapibank.application.port.in.ImportResult;
import com.restapibank.application.port.in.QueryCountriesUseCase;
import com.restapibank.infrastructure.adapter.in.rest.api.CountriesApi;
import com.restapibank.infrastructure.adapter.in.rest.model.Country;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CountryController implements CountriesApi {

    private final ImportCountryUseCase importCountryUseCase;
    private final QueryCountriesUseCase queryCountriesUseCase;
    private final CountryRestMapper mapper;

    public CountryController(ImportCountryUseCase importCountryUseCase,
                             QueryCountriesUseCase queryCountriesUseCase,
                             CountryRestMapper mapper) {
        this.importCountryUseCase = importCountryUseCase;
        this.queryCountriesUseCase = queryCountriesUseCase;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<Country> importCountry(String code) {
        ImportResult result = importCountryUseCase.importCountry(code);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(mapper.toDto(result.country()));
    }

    @Override
    public ResponseEntity<List<Country>> getCountries() {
        return ResponseEntity.ok(mapper.toDtoList(queryCountriesUseCase.findAll()));
    }

    @Override
    public ResponseEntity<Country> getCountryById(Long id) {
        return ResponseEntity.ok(mapper.toDto(queryCountriesUseCase.findById(id)));
    }
}
