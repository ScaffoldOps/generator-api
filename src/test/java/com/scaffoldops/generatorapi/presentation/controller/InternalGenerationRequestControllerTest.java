package com.scaffoldops.generatorapi.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffoldops.generatorapi.application.port.in.UpdateDeploymentRequestStatusUseCase;
import com.scaffoldops.generatorapi.application.port.in.UpdateGenerationRequestStatusUseCase;
import com.scaffoldops.generatorapi.domain.model.DeploymentStatus;
import com.scaffoldops.generatorapi.domain.model.GenerationStatus;
import com.scaffoldops.generatorapi.presentation.config.SecurityConfiguration;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalGenerationRequestController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfiguration.class,
        InternalGenerationRequestControllerTest.MockConfig.class
})
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class InternalGenerationRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UpdateGenerationRequestStatusUseCase updateGenerationRequestStatusUseCase;

    @Autowired
    private UpdateDeploymentRequestStatusUseCase updateDeploymentRequestStatusUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldUpdateGenerationRequestGenerationStatus() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(updateGenerationRequestStatusUseCase.updateGenerationStatus(eq(requestId), any()))
                .thenReturn(UpdateGenerationRequestStatusUseCase.Result.UPDATED);

        mockMvc.perform(patch("/internal/generation-requests/{requestId}/generation-status", requestId)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "generationStatus": "GENERATING",
                                  "message": "Generation started",
                                  "artifactRef": "s3://artifacts/billing.zip",
                                  "imageRef": "registry/billing:latest"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(updateGenerationRequestStatusUseCase).updateGenerationStatus(
                requestId,
                new UpdateGenerationRequestStatusUseCase.Command(
                        GenerationStatus.GENERATING,
                        "Generation started",
                        "s3://artifacts/billing.zip",
                        "registry/billing:latest"
                )
        );
    }

    @Test
    void shouldUpdateGenerationRequestDeploymentStatus() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(updateDeploymentRequestStatusUseCase.updateDeploymentStatus(eq(requestId), any()))
                .thenReturn(UpdateDeploymentRequestStatusUseCase.Result.UPDATED);

        mockMvc.perform(patch("/internal/generation-requests/{requestId}/deployment-status", requestId)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deploymentStatus": "DEPLOYING",
                                  "message": "Deployment started",
                                  "imageRef": "registry/billing:latest"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(updateDeploymentRequestStatusUseCase).updateDeploymentStatus(
                requestId,
                new UpdateDeploymentRequestStatusUseCase.Command(
                        DeploymentStatus.DEPLOYING,
                        "Deployment started",
                        "registry/billing:latest"
                )
        );
    }

    @Test
    void shouldReturnNotFoundWhenGenerationRequestDoesNotExist() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(updateGenerationRequestStatusUseCase.updateGenerationStatus(eq(requestId), any()))
                .thenReturn(UpdateGenerationRequestStatusUseCase.Result.NOT_FOUND);

        mockMvc.perform(patch("/internal/generation-requests/{requestId}/generation-status", requestId)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generationStatus\":\"GENERATING\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Generation request not found"));
    }

    @Test
    void shouldReturnConflictWhenGenerationTransitionIsInvalid() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(updateGenerationRequestStatusUseCase.updateGenerationStatus(eq(requestId), any()))
                .thenReturn(UpdateGenerationRequestStatusUseCase.Result.INVALID_TRANSITION);

        mockMvc.perform(patch("/internal/generation-requests/{requestId}/generation-status", requestId)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generationStatus\":\"GENERATION_FAILED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Invalid generation request status transition"));
    }

    @Test
    void shouldReturnConflictWhenDeploymentTransitionIsInvalid() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(updateDeploymentRequestStatusUseCase.updateDeploymentStatus(eq(requestId), any()))
                .thenReturn(UpdateDeploymentRequestStatusUseCase.Result.INVALID_TRANSITION);

        mockMvc.perform(patch("/internal/generation-requests/{requestId}/deployment-status", requestId)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deploymentStatus\":\"DEPLOYED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Invalid deployment request status transition"));
    }

    @Test
    void shouldReturnBadRequestWhenGenerationStatusIsUnsupported() throws Exception {
        mockMvc.perform(patch("/internal/generation-requests/{requestId}/generation-status", UUID.randomUUID())
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generationStatus\":\"DEPLOYED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void shouldReturnBadRequestWhenRequestIdIsInvalid() throws Exception {
        mockMvc.perform(patch("/internal/generation-requests/{requestId}/generation-status", "not-a-uuid")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generationStatus\":\"GENERATING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for requestId"));
    }

    @TestConfiguration
    static class MockConfig {

        @Bean
        UpdateGenerationRequestStatusUseCase updateGenerationRequestStatusUseCase() {
            return mock(UpdateGenerationRequestStatusUseCase.class);
        }

        @Bean
        UpdateDeploymentRequestStatusUseCase updateDeploymentRequestStatusUseCase() {
            return mock(UpdateDeploymentRequestStatusUseCase.class);
        }

        @Bean
        GenerationRequestApiMapper generationRequestApiMapper(ObjectMapper objectMapper) {
            return new GenerationRequestApiMapper(objectMapper);
        }
    }
}
