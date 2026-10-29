package com.restapibank.infrastructure.config;

import com.restapibank.application.port.in.ImportCountryUseCase;
import com.restapibank.application.port.in.QueryCountriesUseCase;
import com.restapibank.application.port.out.CountryProviderPort;
import com.restapibank.application.port.out.CountryRepositoryPort;
import com.restapibank.application.service.ImportCountryService;
import com.restapibank.application.service.QueryCountriesService;
import com.restapibank.infrastructure.adapter.in.rest.ErrorResponseFactory;
import com.restapibank.infrastructure.adapter.out.worldbank.WorldBankProperties;
import com.restapibank.infrastructure.security.BearerTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(WorldBankProperties.class)
public class ApplicationConfig {

    @Bean
    FilterRegistrationBean<BearerTokenFilter> bearerTokenFilter(
            @Value("${app.security.bearer-token:}") String bearerToken, ErrorResponseFactory errors) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new IllegalStateException("API_BEARER_TOKEN no está configurado");
        }
        FilterRegistrationBean<BearerTokenFilter> registration =
                new FilterRegistrationBean<>(new BearerTokenFilter(bearerToken.strip(), errors));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    RestClient worldBankRestClient(WorldBankProperties properties) {
        Duration timeout = Duration.ofSeconds(properties.timeoutSeconds());
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    }

    @Bean
    ImportCountryUseCase importCountryUseCase(CountryRepositoryPort repository, CountryProviderPort provider) {
        return new ImportCountryService(repository, provider);
    }

    @Bean
    QueryCountriesUseCase queryCountriesUseCase(CountryRepositoryPort repository) {
        return new QueryCountriesService(repository);
    }
}
