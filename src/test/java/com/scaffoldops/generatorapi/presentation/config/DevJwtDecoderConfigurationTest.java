package com.scaffoldops.generatorapi.presentation.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;

class DevJwtDecoderConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DevJwtDecoderConfiguration.class);

    @Test
    void shouldCreateCustomJwtDecoderWhenJwkSetUriIsProvided() {
        contextRunner
                .withPropertyValues(
                        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080/realms/scaffoldops-dev",
                        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://keycloak-dev.security.svc.cluster.local:8080/realms/scaffoldops-dev/protocol/openid-connect/certs"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                    assertThat(context.getBean(JwtDecoder.class)).isInstanceOf(NimbusJwtDecoder.class);
                });
    }

    @Test
    void shouldNotCreateCustomJwtDecoderWithoutDevJwtProperties() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(JwtDecoder.class));
    }
}
