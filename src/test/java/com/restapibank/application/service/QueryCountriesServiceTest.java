package com.restapibank.application.service;

import com.restapibank.application.port.out.CountryRepositoryPort;
import com.restapibank.domain.exception.CountryNotFoundException;
import com.restapibank.domain.model.Country;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryCountriesServiceTest {

    private static final Country COLOMBIA = new Country(1L, "CO", "COL", "Colombia", null, null, null, null, null);
    private static final Country ECUADOR = new Country(2L, "EC", "ECU", "Ecuador", null, null, null, null, null);

    private CountryRepositoryPort repository;
    private QueryCountriesService service;

    @BeforeEach
    void setUp() {
        repository = mock(CountryRepositoryPort.class);
        service = new QueryCountriesService(repository);
    }

    @Test
    void findAllReturnsEmptyListWhenNothingStored() {
        when(repository.findAll()).thenReturn(List.of());

        assertThat(service.findAll()).isEmpty();
    }

    @Test
    void findAllReturnsCountriesInRepositoryOrder() {
        when(repository.findAll()).thenReturn(List.of(COLOMBIA, ECUADOR));

        assertThat(service.findAll()).containsExactly(COLOMBIA, ECUADOR);
    }

    @Test
    void findByIdReturnsStoredCountry() {
        when(repository.findById(1L)).thenReturn(Optional.of(COLOMBIA));

        assertThat(service.findById(1L)).isEqualTo(COLOMBIA);
    }

    @Test
    void findByIdThrowsNotFoundWhenAbsent() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(CountryNotFoundException.class)
                .hasMessageContaining("999");
    }
}
