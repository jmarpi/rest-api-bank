package com.restapibank.infrastructure.adapter.out.worldbank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.world-bank")
public record WorldBankProperties(
        @DefaultValue("https://api.worldbank.org/v2") String baseUrl,
        @DefaultValue("10") int timeoutSeconds) {
}
