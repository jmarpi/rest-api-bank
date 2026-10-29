package com.restapibank.infrastructure.adapter.out.worldbank;

import com.restapibank.domain.exception.ExternalServiceException;
import com.restapibank.domain.model.Country;
import com.restapibank.domain.model.CountryCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WorldBankCountryAdapterTest {

    private static final String BASE_URL = "http://worldbank.test/v2";

    private static final String COLOMBIA = """
            [
              {"page": 1, "pages": 1, "per_page": "50", "total": 1},
              [
                {
                  "id": "COL",
                  "iso2Code": "CO",
                  "name": "Colombia",
                  "region": {"id": "LCN", "iso2code": "ZJ", "value": "Latin America & Caribbean "},
                  "incomeLevel": {"id": "UMC", "iso2code": "XT", "value": "Upper middle income"},
                  "capitalCity": "Bogota",
                  "longitude": "-74.082",
                  "latitude": "4.60987"
                }
              ]
            ]
            """;

    private static final String AGGREGATE = """
            [
              {"page": 1, "pages": 1, "per_page": "50", "total": 1},
              [
                {
                  "id": "LCN",
                  "iso2Code": "ZJ",
                  "name": "Latin America & Caribbean ",
                  "region": {"id": "NA", "iso2code": "NA", "value": "Aggregates"},
                  "incomeLevel": {"id": "NA", "iso2code": "NA", "value": "Aggregates"},
                  "capitalCity": "",
                  "longitude": "",
                  "latitude": ""
                }
              ]
            ]
            """;

    private static final String INVALID_VALUE = """
            [{"message": [{"id": "120", "key": "Invalid value", "value": "The provided parameter value is not valid"}]}]
            """;

    private MockRestServiceServer server;
    private WorldBankCountryAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new WorldBankCountryAdapter(builder.build());
    }

    private void respond(String code, String body) {
        server.expect(requestTo(BASE_URL + "/country/" + code + "?format=json"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void mapsCountryFields() {
        respond("CO", COLOMBIA);

        Optional<Country> result = adapter.fetchByCode(new CountryCode("CO"));

        assertThat(result).contains(new Country(null, "CO", "COL", "Colombia", "Bogota", "Latin America & Caribbean",
                "Upper middle income", new BigDecimal("4.60987"), new BigDecimal("-74.082")));
        server.verify();
    }

    @Test
    void leavesMissingFieldsEmpty() {
        respond("LCN", AGGREGATE);

        Country country = adapter.fetchByCode(new CountryCode("LCN")).orElseThrow();

        assertThat(country.alpha2Code()).isEqualTo("ZJ");
        assertThat(country.name()).isEqualTo("Latin America & Caribbean");
        assertThat(country.capital()).isNull();
        assertThat(country.latitude()).isNull();
        assertThat(country.longitude()).isNull();
    }

    @Test
    void leavesNonNumericCoordinatesEmpty() {
        respond("CO", COLOMBIA.replace("\"4.60987\"", "\"abc\""));

        Country country = adapter.fetchByCode(new CountryCode("CO")).orElseThrow();

        assertThat(country.latitude()).isNull();
        assertThat(country.longitude()).isEqualByComparingTo("-74.082");
    }

    @Test
    void returnsEmptyWhenWorldBankReportsInvalidValue() {
        respond("XX", INVALID_VALUE);

        assertThat(adapter.fetchByCode(new CountryCode("XX"))).isEmpty();
    }

    @Test
    void returnsEmptyWhenCountryListIsEmptyOrNull() {
        respond("XX", "[{\"page\": 1, \"total\": 0}, []]");
        assertThat(adapter.fetchByCode(new CountryCode("XX"))).isEmpty();

        server.reset();
        respond("XX", "[{\"page\": 1, \"total\": 0}, null]");
        assertThat(adapter.fetchByCode(new CountryCode("XX"))).isEmpty();
    }

    @Test
    void failsOnServerError() {
        server.expect(requestTo(BASE_URL + "/country/CO?format=json"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> adapter.fetchByCode(new CountryCode("CO")))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void failsOnNetworkError() {
        server.expect(requestTo(BASE_URL + "/country/CO?format=json"))
                .andRespond(withException(new IOException("timeout")));

        assertThatThrownBy(() -> adapter.fetchByCode(new CountryCode("CO")))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void failsOnInvalidJson() {
        respond("CO", "<html>not json</html>");

        assertThatThrownBy(() -> adapter.fetchByCode(new CountryCode("CO")))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void failsOnUnexpectedStructure() {
        respond("CO", "{\"unexpected\": true}");

        assertThatThrownBy(() -> adapter.fetchByCode(new CountryCode("CO")))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void failsWhenAlpha2IsMissing() {
        respond("CO", COLOMBIA.replace("\"iso2Code\": \"CO\",", ""));

        assertThatThrownBy(() -> adapter.fetchByCode(new CountryCode("CO")))
                .isInstanceOf(ExternalServiceException.class);
    }
}
