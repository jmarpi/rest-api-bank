package com.restapibank.infrastructure.adapter.out.persistence;

import com.restapibank.application.port.out.CountryRepositoryPort;
import com.restapibank.domain.model.Country;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class CountryPersistenceAdapter implements CountryRepositoryPort {

    private final CountryJpaRepository repository;

    public CountryPersistenceAdapter(CountryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Country> findById(long id) {
        return repository.findById(id).map(CountryPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Country> findByAlpha2(String alpha2Code) {
        return repository.findByAlpha2Code(alpha2Code).map(CountryPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Country> findByAlpha3(String alpha3Code) {
        return repository.findByAlpha3Code(alpha3Code).map(CountryPersistenceAdapter::toDomain);
    }

    @Override
    public List<Country> findAll() {
        return repository.findAllByOrderByAlpha2CodeAsc().stream()
                .map(CountryPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public Country save(Country country) {
        try {
            return upsert(country);
        } catch (DataIntegrityViolationException e) {
            // Otra importación concurrente insertó el mismo alpha-2: se reintenta como actualización.
            return upsert(country);
        }
    }

    private Country upsert(Country country) {
        CountryEntity entity = repository.findByAlpha2Code(country.alpha2Code())
                .orElseGet(CountryEntity::new);
        copy(country, entity);
        return toDomain(repository.saveAndFlush(entity));
    }

    private static void copy(Country source, CountryEntity target) {
        target.setAlpha2Code(source.alpha2Code());
        target.setAlpha3Code(source.alpha3Code());
        target.setName(source.name());
        target.setCapital(source.capital());
        target.setRegion(source.region());
        target.setIncomeLevel(source.incomeLevel());
        target.setLatitude(source.latitude());
        target.setLongitude(source.longitude());
    }

    private static Country toDomain(CountryEntity entity) {
        return new Country(
                entity.getId(),
                entity.getAlpha2Code(),
                entity.getAlpha3Code(),
                entity.getName(),
                entity.getCapital(),
                entity.getRegion(),
                entity.getIncomeLevel(),
                stripZeros(entity.getLatitude()),
                stripZeros(entity.getLongitude()));
    }

    // NUMERIC(9,6) rellena con ceros (4.609870); se devuelven tal como los informó el Banco Mundial.
    private static BigDecimal stripZeros(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }
}
