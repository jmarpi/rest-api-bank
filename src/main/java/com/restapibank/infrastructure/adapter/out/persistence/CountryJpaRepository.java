package com.restapibank.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CountryJpaRepository extends JpaRepository<CountryEntity, Long> {

    Optional<CountryEntity> findByAlpha2Code(String alpha2Code);

    Optional<CountryEntity> findByAlpha3Code(String alpha3Code);

    List<CountryEntity> findAllByOrderByAlpha2CodeAsc();
}
