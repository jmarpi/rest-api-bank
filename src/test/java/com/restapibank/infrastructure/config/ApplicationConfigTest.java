package com.restapibank.infrastructure.config;

import com.restapibank.application.port.out.CountryProviderPort;
import com.restapibank.application.port.out.CountryRepositoryPort;
import com.restapibank.infrastructure.adapter.in.rest.ErrorResponseFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ApplicationConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ApplicationConfig.class)
            .withBean(ErrorResponseFactory.class, () -> new ErrorResponseFactory(JsonMapper.builder().build()))
            .withBean(CountryRepositoryPort.class, () -> mock(CountryRepositoryPort.class))
            .withBean(CountryProviderPort.class, () -> mock(CountryProviderPort.class));

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void failsToStartWithoutBearerToken(String token) {
        runner.withPropertyValues("app.security.bearer-token=" + token)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("API_BEARER_TOKEN"));
    }

    @Test
    void failsToStartWhenBearerTokenIsNotDefined() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startsWithBearerToken() {
        runner.withPropertyValues("app.security.bearer-token=secret")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
