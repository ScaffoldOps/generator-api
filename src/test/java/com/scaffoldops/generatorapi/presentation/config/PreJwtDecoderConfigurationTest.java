package com.scaffoldops.generatorapi.presentation.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreJwtDecoderConfigurationTest {

    private static final String ISSUER =
            "http://keycloak-pre.security.svc.cluster.local:8080/realms/scaffoldops-pre";

    @Test
    void shouldUseJwksWithoutDiscoveryAndRejectWrongIssuerAndExpiredTokens() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("pre-test").generate();
        AtomicInteger jwksRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/certs", exchange -> {
            jwksRequests.incrementAndGet();
            byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            String valid = token(key, ISSUER, Instant.now().plusSeconds(300));
            String wrongIssuer = token(key, ISSUER.replace("scaffoldops-pre", "scaffoldops-dev"),
                    Instant.now().plusSeconds(300));
            String expired = token(key, ISSUER, Instant.now().minusSeconds(300));
            String wrongSignature = token(new RSAKeyGenerator(2048).keyID("pre-test").generate(),
                    ISSUER, Instant.now().plusSeconds(300));

            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class,
                            OAuth2ResourceServerAutoConfiguration.class))
                    .withUserConfiguration(DevJwtDecoderConfiguration.class)
                    .withPropertyValues(
                            "spring.profiles.active=pre",
                            "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + ISSUER,
                            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://127.0.0.1:"
                                    + server.getAddress().getPort() + "/certs")
                    .run(context -> {
                        assertThat(context).hasSingleBean(JwtDecoder.class);
                        assertThat(jwksRequests.get()).isZero();
                        JwtDecoder decoder = context.getBean(JwtDecoder.class);
                        assertThat(decoder.decode(valid).getSubject()).isEqualTo("worker");
                        assertThatThrownBy(() -> decoder.decode(wrongIssuer))
                                .isInstanceOf(JwtValidationException.class);
                        assertThatThrownBy(() -> decoder.decode(expired))
                                .isInstanceOf(JwtValidationException.class);
                        assertThatThrownBy(() -> decoder.decode(wrongSignature))
                                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
                    });
        } finally {
            server.stop(0);
        }
    }

    private static String token(RSAKey key, String issuer, Instant expiresAt) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                new JWTClaimsSet.Builder().issuer(issuer).subject("worker")
                        .expirationTime(Date.from(expiresAt)).build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
