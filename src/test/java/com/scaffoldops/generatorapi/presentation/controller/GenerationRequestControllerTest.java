package com.scaffoldops.generatorapi.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffoldops.generatorapi.application.model.GenerationRequestFilters;
import com.scaffoldops.generatorapi.application.port.in.CreateGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.DeleteGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.GetGenerationRequestUseCase;
import com.scaffoldops.generatorapi.application.port.in.ListGenerationRequestsUseCase;
import com.scaffoldops.generatorapi.application.port.in.RequestDeploymentUseCase;
import com.scaffoldops.generatorapi.application.port.in.RequestUndeploymentUseCase;
import com.scaffoldops.generatorapi.presentation.config.SecurityConfiguration;
import com.scaffoldops.generatorapi.domain.model.DeploymentStatus;
import com.scaffoldops.generatorapi.domain.model.DeploymentTarget;
import com.scaffoldops.generatorapi.domain.model.GenerationRequest;
import com.scaffoldops.generatorapi.domain.model.GenerationStatus;
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

    @Autowired
    private RequestDeploymentUseCase requestDeploymentUseCase;

    @Autowired
    private RequestUndeploymentUseCase requestUndeploymentUseCase;

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
                .andExpect(jsonPath("$.generationStatus").value("RECEIVED"))
                .andExpect(jsonPath("$.deploymentStatus").value("NOT_DEPLOYED"))
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
                .andExpect(jsonPath("$.generationStatus").value("RECEIVED"))
                .andExpect(jsonPath("$.deploymentStatus").value("NOT_DEPLOYED"))
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
    void shouldRequestDeployment() throws Exception {
        UUID id = UUID.randomUUID();
        when(requestDeploymentUseCase.requestDeployment(
                new RequestDeploymentUseCase.Command(id, "scaffoldops-dev", 2)
        )).thenReturn(RequestDeploymentUseCase.Result.ACCEPTED);

        mockMvc.perform(post("/generation-requests/{id}/deploy", id)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "namespace": "scaffoldops-dev",
                                  "replicas": 2
                                }
                                """))
                .andExpect(status().isAccepted());
    }

    @Test
    void shouldReturnConflictWhenDeploymentStateIsInvalid() throws Exception {
        UUID id = UUID.randomUUID();
        when(requestDeploymentUseCase.requestDeployment(
                new RequestDeploymentUseCase.Command(id, "scaffoldops-dev", null)
        )).thenReturn(RequestDeploymentUseCase.Result.INVALID_TRANSITION);

        mockMvc.perform(post("/generation-requests/{id}/deploy", id)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"namespace\":\"scaffoldops-dev\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Generation request cannot be deployed from its current lifecycle state"));
    }

    @Test
    void shouldRequestUndeployment() throws Exception {
        UUID id = UUID.randomUUID();
        when(requestUndeploymentUseCase.requestUndeployment(id)).thenReturn(RequestUndeploymentUseCase.Result.ACCEPTED);

        mockMvc.perform(delete("/generation-requests/{id}/deployment", id).with(jwt()))
                .andExpect(status().isAccepted());
    }

    @Test
    void shouldReturnConflictWhenUndeploymentStateIsInvalid() throws Exception {
        UUID id = UUID.randomUUID();
        when(requestUndeploymentUseCase.requestUndeployment(id))
                .thenReturn(RequestUndeploymentUseCase.Result.INVALID_TRANSITION);

        mockMvc.perform(delete("/generation-requests/{id}/deployment", id).with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Generation request cannot be undeployed from its current lifecycle state"));
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
                GenerationStatus.RECEIVED,
                DeploymentStatus.NOT_DEPLOYED,
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
                        .queryParam("generationStatus", "RECEIVED")
                        .queryParam("deploymentStatus", "NOT_DEPLOYED")
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
    void shouldReturnBadRequestWhenGenerationStatusFilterIsInvalid() throws Exception {
        mockMvc.perform(get("/generation-requests")
                        .with(jwt())
                        .queryParam("generationStatus", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for generationStatus"));
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
                GenerationStatus.RECEIVED,
                DeploymentStatus.NOT_DEPLOYED,
                "{\"name\":\"billing-service\"}",
                null,
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
        RequestDeploymentUseCase requestDeploymentUseCase() {
            return mock(RequestDeploymentUseCase.class);
        }

        @Bean
        RequestUndeploymentUseCase requestUndeploymentUseCase() {
            return mock(RequestUndeploymentUseCase.class);
        }

        @Bean
        GenerationRequestApiMapper generationRequestApiMapper(ObjectMapper objectMapper) {
            return new GenerationRequestApiMapper(objectMapper);
        }
    }
}
