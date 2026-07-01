package com.scaffoldops.generatorapi.presentation.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@EnableConfigurationProperties(DevJwtDecoderConfiguration.DevJwtProperties.class)
class DevJwtDecoderConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "spring.security.oauth2.resourceserver.jwt", name = "jwk-set-uri")
    JwtDecoder jwtDecoder(DevJwtProperties properties) {
        return NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
    }

    @ConfigurationProperties(prefix = "spring.security.oauth2.resourceserver.jwt")
    record DevJwtProperties(String jwkSetUri) {
    }
}
