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
                .andExpect(jsonPath("$.generation.status").value("RECEIVED"))
                .andExpect(jsonPath("$.deployment.status").value("NOT_DEPLOYED"))
                .andExpect(jsonPath("$.timestamps.updatedAt").value("2026-03-07T10:15:30Z"))
                .andExpect(jsonPath("$.timestamps.createdAt").value("2026-03-07T10:15:30Z"))
                .andExpect(jsonPath("$.features.database").value(true))
                .andExpect(jsonPath("$.features.restApi").value(true))
                .andExpect(jsonPath("$.features.security").value(true))
                .andExpect(jsonPath("$.features.messaging").value(false))
                .andExpect(jsonPath("$.generation.retryCount").value(0))
                .andExpect(jsonPath("$.*", org.hamcrest.Matchers.hasSize(8)));
    }

    @Test
    void shouldReturnGenerationRequestById() throws Exception {
        UUID id = UUID.randomUUID();
        when(getGenerationRequestUseCase.getById(id)).thenReturn(Optional.of(sample(id)));

        mockMvc.perform(get("/generation-requests/{id}", id).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("billing-service"))
                .andExpect(jsonPath("$.deploymentTarget").value("KUBERNETES"))
                .andExpect(jsonPath("$.generation.status").value("RECEIVED"))
                .andExpect(jsonPath("$.deployment.status").value("NOT_DEPLOYED"))
                .andExpect(jsonPath("$.timestamps.updatedAt").value("2026-03-07T10:15:30Z"))
                .andExpect(jsonPath("$.timestamps.createdAt").value("2026-03-07T10:15:30Z"))
                .andExpect(jsonPath("$.features.database").value(true))
                .andExpect(jsonPath("$.features.restApi").value(true))
                .andExpect(jsonPath("$.features.security").value(true))
                .andExpect(jsonPath("$.features.messaging").value(false))
                .andExpect(jsonPath("$.generation.retryCount").value(0))
                .andExpect(jsonPath("$.*", org.hamcrest.Matchers.hasSize(8)));
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

        mockMvc.perform(post("/generation-requests/{id}/deployment", id)
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "{\"namespace\":\"Bad_Name\",\"replicas\":1}",
            "{\"namespace\":\"generated-dev\",\"replicas\":0}",
            "{\"namespace\":\"generated-dev\",\"replicas\":21}",
            "{\"namespace\":\"generated-dev\"}"})
    void rejectsInvalidDeploymentBody(String body) throws Exception {
        org.mockito.Mockito.clearInvocations(requestDeploymentUseCase);
        mockMvc.perform(post("/generation-requests/{id}/deployment", UUID.randomUUID())
                        .with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(requestDeploymentUseCase);
    }

    @Test
    void shouldReturnConflictWhenDeploymentStateIsInvalid() throws Exception {
        UUID id = UUID.randomUUID();
        when(requestDeploymentUseCase.requestDeployment(
                new RequestDeploymentUseCase.Command(id, "scaffoldops-dev", 1)
        )).thenReturn(RequestDeploymentUseCase.Result.INVALID_TRANSITION);

        mockMvc.perform(post("/generation-requests/{id}/deployment", id)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"namespace\":\"scaffoldops-dev\",\"replicas\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Deployment requires GENERATED with nonblank artifactRef and imageRef, an eligible deployment state, and a valid namespace and replica count"));
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
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].features.database").value(true))
                .andExpect(jsonPath("$[0].generation.status").value("RECEIVED"))
                .andExpect(jsonPath("$[0].deployment.status").value("NOT_DEPLOYED"))
                .andExpect(jsonPath("$[0].timestamps.createdAt").value("2026-03-07T10:15:30Z"));
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

    @Test
    void shouldSerializeNullableResponseFieldsAsNull() throws Exception {
        UUID id = UUID.randomUUID();
        when(getGenerationRequestUseCase.getById(id)).thenReturn(Optional.of(sample(id)));
        var result = mockMvc.perform(get("/generation-requests/{id}", id).with(jwt()))
                .andExpect(status().isOk()).andReturn();
        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        for (String field : List.of("/generation/message", "/generation/artifactRef", "/generation/imageRef", "/deployment/namespace", "/generation/failureStage")) {
            org.assertj.core.api.Assertions.assertThat(!json.at(field).isMissingNode()).as(field).isTrue();
            org.assertj.core.api.Assertions.assertThat(json.at(field).isNull()).as(field).isTrue();
        }
    }

    @Test
    void shouldSerializeNullableResponseFieldsAsStrings() throws Exception {
        UUID id = UUID.randomUUID();
        GenerationRequest base = sample(id);
        GenerationRequest request = new GenerationRequest(base.id(), base.name(), base.template(),
                base.database(), base.restApi(), base.security(), base.messaging(), base.deploymentTarget(),
                base.generationStatus(), base.deploymentStatus(), base.specJson(),
                "Generated", "s3://artifacts/billing.zip", "registry/billing:latest", "scaffoldops-dev",
                base.createdAt(), base.updatedAt(), "IMAGE_PUSH", 2);
        when(getGenerationRequestUseCase.getById(id)).thenReturn(Optional.of(request));
        mockMvc.perform(get("/generation-requests/{id}", id).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generation.failureStage").value("IMAGE_PUSH"))
                .andExpect(jsonPath("$.generation.retryCount").value(2))
                .andExpect(jsonPath("$.generation.message").value("Generated"))
                .andExpect(jsonPath("$.generation.artifactRef").value("s3://artifacts/billing.zip"))
                .andExpect(jsonPath("$.generation.imageRef").value("registry/billing:latest"))
                .andExpect(jsonPath("$.deployment.namespace").value("scaffoldops-dev"));
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
