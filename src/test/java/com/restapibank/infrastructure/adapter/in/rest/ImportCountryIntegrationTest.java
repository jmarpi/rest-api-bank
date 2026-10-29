package com.restapibank.infrastructure.adapter.in.rest;

import com.restapibank.domain.exception.ExternalServiceException;
import com.restapibank.domain.model.Country;
import com.restapibank.domain.model.CountryCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImportCountryIntegrationTest extends AbstractIntegrationTest {

    private static final Country COLOMBIA = new Country(null, "CO", "COL", "Colombia", "Bogota",
            "Latin America & Caribbean", "Upper middle income", new BigDecimal("4.60987"), new BigDecimal("-74.082"));

    private int storedCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM country", Integer.class);
    }

    @Test
    void createsCountryAndReturns201() throws Exception {
        when(countryProvider.fetchByCode(new CountryCode("CO"))).thenReturn(Optional.of(COLOMBIA));

        mockMvc.perform(post("/api/countries/import").param("code", "CO").header("Authorization", bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.alpha2Code").value("CO"))
                .andExpect(jsonPath("$.alpha3Code").value("COL"))
                .andExpect(jsonPath("$.name").value("Colombia"))
                .andExpect(jsonPath("$.capital").value("Bogota"))
                .andExpect(jsonPath("$.region").value("Latin America & Caribbean"))
                .andExpect(jsonPath("$.incomeLevel").value("Upper middle income"))
                .andExpect(jsonPath("$.latitude").value(4.60987))
                .andExpect(jsonPath("$.longitude").value(-74.082));

        assertThat(storedCount()).isEqualTo(1);
    }

    @Test
    void reimportWithAlpha3UpdatesAndReturns200WithoutDuplicates() throws Exception {
        when(countryProvider.fetchByCode(any())).thenReturn(Optional.of(COLOMBIA));

        mockMvc.perform(post("/api/countries/import").param("code", "CO").header("Authorization", bearer()))
                .andExpect(status().isCreated());
        Long id = jdbcTemplate.queryForObject("SELECT id FROM country WHERE alpha2_code = 'CO'", Long.class);
        mockMvc.perform(post("/api/countries/import").param("code", "col").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.alpha2Code").value("CO"));

        assertThat(storedCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"C1", "COLO", "", "   ", "C"})
    void rejectsInvalidCodesWith400(String code) throws Exception {
        mockMvc.perform(post("/api/countries/import").param("code", code).header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/countries/import"));

        verifyNoInteractions(countryProvider);
    }

    @Test
    void invalidCodeMessageIncludesReceivedValue() throws Exception {
        mockMvc.perform(post("/api/countries/import").param("code", "C1").header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El código de país 'C1' no es válido: debe tener 2 o 3 letras."));
    }

    @Test
    void rejectsMissingCodeWith400() throws Exception {
        mockMvc.perform(post("/api/countries/import").header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void returns404WhenWorldBankDoesNotKnowTheCode() throws Exception {
        when(countryProvider.fetchByCode(new CountryCode("XX"))).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/countries/import").param("code", "XX").header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        assertThat(storedCount()).isZero();
    }

    @Test
    void returns502WhenWorldBankFailsForNewCountry() throws Exception {
        when(countryProvider.fetchByCode(any())).thenThrow(new ExternalServiceException("caído"));

        mockMvc.perform(post("/api/countries/import").param("code", "EC").header("Authorization", bearer()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));

        assertThat(storedCount()).isZero();
    }

    @Test
    void returnsStoredDataWhenWorldBankFailsForExistingCountry() throws Exception {
        insertCountry("CO", "COL", "Colombia previa");
        when(countryProvider.fetchByCode(any())).thenThrow(new ExternalServiceException("caído"));

        mockMvc.perform(post("/api/countries/import").param("code", "CO").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Colombia previa"));

        assertThat(jdbcTemplate.queryForObject("SELECT name FROM country WHERE alpha2_code = 'CO'", String.class))
                .isEqualTo("Colombia previa");
    }

    @Test
    void rejectsRequestWithoutTokenWith403() throws Exception {
        mockMvc.perform(post("/api/countries/import").param("code", "CO"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(countryProvider);
    }
}
