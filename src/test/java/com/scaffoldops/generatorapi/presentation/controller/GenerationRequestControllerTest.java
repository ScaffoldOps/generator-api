package com.scaffoldops.generatorapi.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffoldops.generatorapi.application.model.GenerationRequestFilters;
import com.scaffoldops.generatorapi.application.port.in.CreateGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.DeleteGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.GetGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.ListGenerationRequestsUseCase;
import com.scaffoldops.generatorapi.presentation.config.SecurityConfiguration;
import com.scaffoldops.generatorapi.domain.model.DeploymentTarget;
import com.scaffoldops.generatorapi.domain.model.GenerationRequest;
import com.scaffoldops.generatorapi.domain.model.GenerationRequestStatus;
import com.scaffoldops.generatorapi.presentation.error.GlobalExceptionHandler;
import com.scaffoldops.generatorapi.presentation.mapper.GenerationRequestApiMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GenerationRequestController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfiguration.class,
        GenerationRequestControllerTest.MockConfig.class
})
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class GenerationRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CreateGenerationRequestUseCase createGenerationRequestUseCase;

    @Autowired
    private DeleteGenerationRequestUseCase deleteGenerationRequestUseCase;

    @Autowired
    private GetGenerationRequestUseCase getGenerationRequestUseCase;

    @Autowired
    private ListGenerationRequestsUseCase listGenerationRequestsUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldCreateGenerationRequest() throws Exception {
        UUID id = UUID.randomUUID();
        when(createGenerationRequestUseCase.create(any())).thenReturn(sample(id));

        mockMvc.perform(post("/generation-requests")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RequestBodyFixture())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.updatedAt").value("2026-03-07T10:15:30Z"));
    }

    @Test
    void shouldReturnGenerationRequestById() throws Exception {
        UUID id = UUID.randomUUID();
        when(getGenerationRequestUseCase.getById(id)).thenReturn(Optional.of(sample(id)));

        mockMvc.perform(get("/generation-requests/{id}", id).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("billing-service"))
                .andExpect(jsonPath("$.deploymentTarget").value("KUBERNETES"))
                .andExpect(jsonPath("$.updatedAt").value("2026-03-07T10:15:30Z"));
    }

    @Test
    void shouldReturnNotFoundWhenGenerationRequestDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        when(getGenerationRequestUseCase.getById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/generation-requests/{id}", id).with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Generation request not found"));
    }

    @Test
    void shouldDeleteGenerationRequestById() throws Exception {
        UUID id = UUID.randomUUID();
        when(deleteGenerationRequestUseCase.deleteById(id)).thenReturn(true);

        mockMvc.perform(delete("/generation-requests/{id}", id).with(jwt()))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingGenerationRequest() throws Exception {
        UUID id = UUID.randomUUID();
        when(deleteGenerationRequestUseCase.deleteById(id)).thenReturn(false);

        mockMvc.perform(delete("/generation-requests/{id}", id).with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Generation request not found"));
    }

    @Test
    void shouldListGenerationRequests() throws Exception {
        UUID id = UUID.randomUUID();
        when(listGenerationRequestsUseCase.getAll(GenerationRequestFilters.empty())).thenReturn(List.of(sample(id)));

        mockMvc.perform(get("/generation-requests").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()));
    }

    @Test
    void shouldListGenerationRequestsUsingFilters() throws Exception {
        UUID id = UUID.randomUUID();
        GenerationRequestFilters filters = new GenerationRequestFilters(
                "billing-service",
                "spring-boot-hexagonal",
                GenerationRequestStatus.RECEIVED,
                DeploymentTarget.KUBERNETES,
                true,
                true,
                true,
                false
        );
        when(listGenerationRequestsUseCase.getAll(eq(filters))).thenReturn(List.of(sample(id)));

        mockMvc.perform(get("/generation-requests")
                        .with(jwt())
                        .queryParam("name", "billing-service")
                        .queryParam("template", "spring-boot-hexagonal")
                        .queryParam("status", "RECEIVED")
                        .queryParam("deploymentTarget", "KUBERNETES")
                        .queryParam("database", "true")
                        .queryParam("restApi", "true")
                        .queryParam("security", "true")
                        .queryParam("messaging", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].name").value("billing-service"));

        verify(listGenerationRequestsUseCase).getAll(filters);
    }

    @Test
    void shouldReturnBadRequestWhenStatusFilterIsInvalid() throws Exception {
        mockMvc.perform(get("/generation-requests")
                        .with(jwt())
                        .queryParam("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for status"));
    }

    @Test
    void shouldValidateRequestBody() throws Exception {
        mockMvc.perform(post("/generation-requests")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "template": "spring-boot-hexagonal",
                                  "database": true,
                                  "restApi": true,
                                  "security": true,
                                  "messaging": false,
                                  "deploymentTarget": "KUBERNETES"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name size must be between 1 and 2147483647"));
    }

    @Test
    void shouldReturnUnauthorizedWhenRequestHasNoBearerToken() throws Exception {
        mockMvc.perform(get("/generation-requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    void shouldReturnBadRequestWhenIdIsInvalid() throws Exception {
        mockMvc.perform(get("/generation-requests/{id}", "not-a-uuid").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for id"));
    }

    @Test
    void shouldReturnBadRequestWhenDeleteIdIsInvalid() throws Exception {
        mockMvc.perform(delete("/generation-requests/{id}", "not-a-uuid").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for id"));
    }

    @Test
    void shouldReturnBadRequestWhenRequestBodyIsMalformed() throws Exception {
        mockMvc.perform(post("/generation-requests")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    private GenerationRequest sample(UUID id) {
        return new GenerationRequest(
                id,
                "billing-service",
                "spring-boot-hexagonal",
                true,
                true,
                true,
                false,
                DeploymentTarget.KUBERNETES,
                GenerationRequestStatus.RECEIVED,
                "{\"name\":\"billing-service\"}",
                null,
                null,
                null,
                OffsetDateTime.parse("2026-03-07T10:15:30Z"),
                OffsetDateTime.parse("2026-03-07T10:15:30Z")
        );
    }

    private record RequestBodyFixture(
            String name,
            String template,
            boolean database,
            boolean restApi,
            boolean security,
            boolean messaging,
            String deploymentTarget
    ) {
        private RequestBodyFixture() {
            this(
                    "billing-service",
                    "spring-boot-hexagonal",
                    true,
                    true,
                    true,
                    false,
                    "KUBERNETES"
            );
        }
    }

    @TestConfiguration
    static class MockConfig {

        @Bean
        CreateGenerationRequestUseCase createGenerationRequestUseCase() {
            return mock(CreateGenerationRequestUseCase.class);
        }

        @Bean
        DeleteGenerationRequestUseCase deleteGenerationRequestUseCase() {
            return mock(DeleteGenerationRequestUseCase.class);
        }

        @Bean
        GetGenerationRequestUseCase getGenerationRequestUseCase() {
            return mock(GetGenerationRequestUseCase.class);
        }

        @Bean
        ListGenerationRequestsUseCase listGenerationRequestsUseCase() {
            return mock(ListGenerationRequestsUseCase.class);
        }

        @Bean
        GenerationRequestApiMapper generationRequestApiMapper(ObjectMapper objectMapper) {
            return new GenerationRequestApiMapper(objectMapper);
        }
    }
}
