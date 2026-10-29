package com.restapibank.infrastructure.adapter.in.rest;

import com.restapibank.domain.exception.ExternalServiceException;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ListCountriesIntegrationTest extends AbstractIntegrationTest {

    @Test
    void returnsEmptyArrayWhenNothingStored() throws Exception {
        mockMvc.perform(get("/api/countries").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void returnsAllStoredCountriesOrderedByAlpha2() throws Exception {
        Long ecuadorId = insertCountry("EC", "ECU", "Ecuador");
        Long colombiaId = insertCountry("CO", "COL", "Colombia");

        mockMvc.perform(get("/api/countries").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(colombiaId))
                .andExpect(jsonPath("$[0].alpha2Code").value("CO"))
                .andExpect(jsonPath("$[1].id").value(ecuadorId))
                .andExpect(jsonPath("$[1].alpha2Code").value("EC"))
                .andExpect(jsonPath("$[0].capital").value(nullValue()))
                .andExpect(jsonPath("$[0].latitude").value(nullValue()));
    }

    @Test
    void worksWhenWorldBankIsDown() throws Exception {
        insertCountry("CO", "COL", "Colombia");
        when(countryProvider.fetchByCode(any())).thenThrow(new ExternalServiceException("caído"));

        mockMvc.perform(get("/api/countries").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Colombia"));

        verifyNoInteractions(countryProvider);
    }

    @Test
    void rejectsRequestWithoutTokenWith403() throws Exception {
        mockMvc.perform(get("/api/countries")).andExpect(status().isForbidden());
    }
}
