package com.restapibank.application.service;

import com.restapibank.application.port.in.ImportCountryUseCase;
import com.restapibank.application.port.in.ImportResult;
import com.restapibank.application.port.out.CountryProviderPort;
import com.restapibank.application.port.out.CountryRepositoryPort;
import com.restapibank.domain.exception.CountryNotFoundException;
import com.restapibank.domain.exception.ExternalServiceException;
import com.restapibank.domain.model.Country;
import com.restapibank.domain.model.CountryCode;

import java.util.Optional;

/**
 * Importa un país desde el proveedor externo. Si el país ya estaba almacenado y el proveedor
 * falla o ya no lo reconoce, se devuelven los datos previos sin modificarlos.
 */
public class ImportCountryService implements ImportCountryUseCase {

    private static final System.Logger LOG = System.getLogger(ImportCountryService.class.getName());

    private final CountryRepositoryPort repository;
    private final CountryProviderPort provider;

    public ImportCountryService(CountryRepositoryPort repository, CountryProviderPort provider) {
        this.repository = repository;
        this.provider = provider;
    }

    @Override
    public ImportResult importCountry(String rawCode) {
        CountryCode code = CountryCode.of(rawCode);
        Optional<Country> existing = code.isAlpha2()
                ? repository.findByAlpha2(code.value())
                : repository.findByAlpha3(code.value());

        Optional<Country> fetched;
        try {
            fetched = provider.fetchByCode(code);
        } catch (ExternalServiceException e) {
            if (existing.isPresent()) {
                LOG.log(System.Logger.Level.WARNING,
                        "No se pudo actualizar ''{0}'' desde el Banco Mundial; se devuelven los datos previos: {1}",
                        code.value(), e.getMessage());
                return new ImportResult(existing.get(), false);
            }
            throw e;
        }

        if (fetched.isEmpty()) {
            if (existing.isPresent()) {
                LOG.log(System.Logger.Level.WARNING,
                        "El Banco Mundial no reconoce ''{0}''; se devuelven los datos previos", code.value());
                return new ImportResult(existing.get(), false);
            }
            throw new CountryNotFoundException(code.value());
        }

        Country country = fetched.get();
        boolean created = repository.findByAlpha2(country.alpha2Code()).isEmpty();
        return new ImportResult(repository.save(country), created);
    }
}
