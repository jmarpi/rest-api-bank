package com.restapibank.infrastructure.adapter.in.rest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import com.restapibank.domain.model.Country;
import com.restapibank.domain.model.CountryCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIntegrationTest extends AbstractIntegrationTest {

    static Stream<Arguments> rejectedRequests() {
        String[] headers = {null, "Bearer wrong", "Basic dGVzdA==", "Bearer "};
        return Stream.of(headers).flatMap(header -> Stream.of(
                Arguments.of("POST /api/countries/import?code=CO", header),
                Arguments.of("GET /api/countries", header),
                Arguments.of("GET /api/countries/1", header)));
    }

    private static MockHttpServletRequestBuilder request(String operation) {
        String[] parts = operation.split(" ");
        return parts[0].equals("POST") ? post(parts[1]) : get(parts[1]);
    }

    @ParameterizedTest(name = "{0} con Authorization={1} → 403")
    @MethodSource("rejectedRequests")
    void rejectsRequestsWithoutValidToken(String operation, String header) throws Exception {
        MockHttpServletRequestBuilder request = request(operation);
        if (header != null) {
            request.header("Authorization", header);
        }

        mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value(operation.split(" ")[1].split("\\?")[0]));

        verifyNoInteractions(countryProvider);
    }

    @Test
    void acceptsValidToken() throws Exception {
        when(countryProvider.fetchByCode(new CountryCode("CO"))).thenReturn(Optional.of(new Country(
                null, "CO", "COL", "Colombia", null, null, null, null, null)));

        mockMvc.perform(post("/api/countries/import").param("code", "CO").header("Authorization", bearer()))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsUnknownApiRoutesWithoutTokenBeforeRouting() throws Exception {
        mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isForbidden());
    }

    @Test
    void swaggerIsPublic() throws Exception {
        mockMvc.perform(get("/openapi.yaml")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
