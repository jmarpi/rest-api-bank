package com.restapibank.infrastructure.adapter.in.rest;

import com.restapibank.application.port.out.CountryProviderPort;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.security.bearer-token=" + AbstractIntegrationTest.TOKEN)
public abstract class AbstractIntegrationTest {

    static final String TOKEN = "test-token";

    // Un único contenedor para toda la suite: el contexto de Spring se cachea entre clases de prueba
    // y debe seguir apuntando a la misma base de datos.
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @MockitoBean
    protected CountryProviderPort countryProvider;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM country");
    }

    protected static String bearer() {
        return "Bearer " + TOKEN;
    }

    protected Long insertCountry(String alpha2, String alpha3, String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO country (alpha2_code, alpha3_code, name, capital, region, income_level,
                                     latitude, longitude, created_at, updated_at)
                VALUES (?, ?, ?, NULL, NULL, NULL, NULL, NULL, now(), now())
                RETURNING id
                """, Long.class, alpha2, alpha3, name);
    }
}
