package com.scaffoldops.generatorapi.presentation.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@EnableConfigurationProperties(DevJwtDecoderConfiguration.DevJwtProperties.class)
class DevJwtDecoderConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.security.jwt", name = {"expected-issuer", "jwk-set-uri"})
    JwtDecoder jwtDecoder(DevJwtProperties properties) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(properties.expectedIssuer());
        jwtDecoder.setJwtValidator(validator);
        return jwtDecoder;
    }

    @ConfigurationProperties(prefix = "app.security.jwt")
    record DevJwtProperties(String expectedIssuer, String jwkSetUri) {
    }
}
