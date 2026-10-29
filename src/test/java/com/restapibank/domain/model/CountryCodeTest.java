package com.restapibank.domain.model;

import com.restapibank.domain.exception.InvalidCountryCodeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CountryCodeTest {

    @Test
    void normalizesValidCodes() {
        assertThat(CountryCode.of("CO").value()).isEqualTo("CO");
        assertThat(CountryCode.of("col").value()).isEqualTo("COL");
        assertThat(CountryCode.of(" co ").value()).isEqualTo("CO");
    }

    @Test
    void distinguishesAlpha2FromAlpha3() {
        assertThat(CountryCode.of("CO").isAlpha2()).isTrue();
        assertThat(CountryCode.of("CO").isAlpha3()).isFalse();
        assertThat(CountryCode.of("COL").isAlpha3()).isTrue();
        assertThat(CountryCode.of("COL").isAlpha2()).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "C", "COLO", "C1", "1W", "C-O"})
    void rejectsInvalidCodes(String raw) {
        assertThatThrownBy(() -> CountryCode.of(raw)).isInstanceOf(InvalidCountryCodeException.class);
    }
}
