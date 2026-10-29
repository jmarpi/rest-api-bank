package com.restapibank.infrastructure.adapter.in.rest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GetCountryIntegrationTest extends AbstractIntegrationTest {

    @Test
    void returnsStoredCountryById() throws Exception {
        Long id = insertCountry("CO", "COL", "Colombia");

        mockMvc.perform(get("/api/countries/{id}", id).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.alpha2Code").value("CO"))
                .andExpect(jsonPath("$.name").value("Colombia"));

        verifyNoInteractions(countryProvider);
    }

    @Test
    void returns404WhenNotStored() throws Exception {
        mockMvc.perform(get("/api/countries/999").header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("No se encontró el país con id '999'."))
                .andExpect(jsonPath("$.path").value("/api/countries/999"));

        verifyNoInteractions(countryProvider);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CO", "abc", "0", "-1", "1.5"})
    void returns400ForInvalidId(String id) throws Exception {
        mockMvc.perform(get("/api/countries/{id}", id).header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("El id '" + id + "' no es válido: debe ser un número entero positivo."));
    }

    @Test
    void rejectsRequestWithoutTokenWith403() throws Exception {
        mockMvc.perform(get("/api/countries/1")).andExpect(status().isForbidden());
    }
}
