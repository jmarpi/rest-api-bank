package com.restapibank.application.service;

import com.restapibank.application.port.in.ImportResult;
import com.restapibank.application.port.out.CountryProviderPort;
import com.restapibank.application.port.out.CountryRepositoryPort;
import com.restapibank.domain.exception.CountryNotFoundException;
import com.restapibank.domain.exception.ExternalServiceException;
import com.restapibank.domain.exception.InvalidCountryCodeException;
import com.restapibank.domain.model.Country;
import com.restapibank.domain.model.CountryCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImportCountryServiceTest {

    private static final Country STORED = new Country(1L, "CO", "COL", "Colombia", "Bogota",
            "Latin America & Caribbean", "Lower middle income", new BigDecimal("4.6"), new BigDecimal("-74.0"));
    private static final Country FETCHED = new Country(null, "CO", "COL", "Colombia", "Bogota",
            "Latin America & Caribbean", "Upper middle income", new BigDecimal("4.60987"), new BigDecimal("-74.082"));

    private CountryRepositoryPort repository;
    private CountryProviderPort provider;
    private ImportCountryService service;

    @BeforeEach
    void setUp() {
        repository = mock(CountryRepositoryPort.class);
        provider = mock(CountryProviderPort.class);
        service = new ImportCountryService(repository, provider);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsCountryWhenNotStored() {
        when(repository.findByAlpha2("CO")).thenReturn(Optional.empty());
        when(provider.fetchByCode(new CountryCode("CO"))).thenReturn(Optional.of(FETCHED));

        ImportResult result = service.importCountry("co");

        assertThat(result.created()).isTrue();
        assertThat(result.country()).isEqualTo(FETCHED);
        verify(repository).save(FETCHED);
    }

    @Test
    void updatesCountryWhenAlreadyStored() {
        when(repository.findByAlpha2("CO")).thenReturn(Optional.of(STORED));
        when(provider.fetchByCode(new CountryCode("CO"))).thenReturn(Optional.of(FETCHED));

        ImportResult result = service.importCountry("CO");

        assertThat(result.created()).isFalse();
        assertThat(result.country()).isEqualTo(FETCHED);
        verify(repository).save(FETCHED);
    }

    @Test
    void looksUpByAlpha3WhenCodeHasThreeLetters() {
        when(repository.findByAlpha3("COL")).thenReturn(Optional.of(STORED));
        when(repository.findByAlpha2("CO")).thenReturn(Optional.of(STORED));
        when(provider.fetchByCode(new CountryCode("COL"))).thenReturn(Optional.of(FETCHED));

        ImportResult result = service.importCountry("COL");

        assertThat(result.created()).isFalse();
        verify(repository).findByAlpha3("COL");
    }

    @Test
    void upsertUsesAlpha2ReturnedByProvider() {
        when(repository.findByAlpha3("COL")).thenReturn(Optional.empty());
        when(repository.findByAlpha2("CO")).thenReturn(Optional.of(STORED));
        when(provider.fetchByCode(new CountryCode("COL"))).thenReturn(Optional.of(FETCHED));

        ImportResult result = service.importCountry("COL");

        assertThat(result.created()).isFalse();
    }

    @Test
    void throwsNotFoundWhenProviderHasNoCountryAndNothingStored() {
        when(repository.findByAlpha2("XX")).thenReturn(Optional.empty());
        when(provider.fetchByCode(new CountryCode("XX"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importCountry("XX")).isInstanceOf(CountryNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void propagatesProviderFailureWhenNothingStored() {
        when(repository.findByAlpha2("EC")).thenReturn(Optional.empty());
        when(provider.fetchByCode(new CountryCode("EC"))).thenThrow(new ExternalServiceException("caído"));

        assertThatThrownBy(() -> service.importCountry("EC")).isInstanceOf(ExternalServiceException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void returnsStoredDataWhenProviderFails() {
        when(repository.findByAlpha2("CO")).thenReturn(Optional.of(STORED));
        when(provider.fetchByCode(new CountryCode("CO"))).thenThrow(new ExternalServiceException("caído"));

        ImportResult result = service.importCountry("CO");

        assertThat(result.created()).isFalse();
        assertThat(result.country()).isEqualTo(STORED);
        verify(repository, never()).save(any());
    }

    @Test
    void returnsStoredDataWhenProviderNoLongerKnowsCountry() {
        when(repository.findByAlpha2("CO")).thenReturn(Optional.of(STORED));
        when(provider.fetchByCode(new CountryCode("CO"))).thenReturn(Optional.empty());

        ImportResult result = service.importCountry("CO");

        assertThat(result.created()).isFalse();
        assertThat(result.country()).isEqualTo(STORED);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInvalidCodeWithoutCallingProvider() {
        assertThatThrownBy(() -> service.importCountry("C1")).isInstanceOf(InvalidCountryCodeException.class);
        verifyNoInteractions(provider);
    }
}
