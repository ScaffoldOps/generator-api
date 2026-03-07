package com.scaffoldops.generatorapi.infrastructure.persistence.entity;

import com.scaffoldops.generatorapi.domain.model.DeploymentTarget;
import com.scaffoldops.generatorapi.domain.model.GenerationRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "generation_requests")
public class GenerationRequestEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String template;

    @Column(nullable = false)
    private boolean database;

    @Column(nullable = false)
    private boolean restApi;

    @Column(nullable = false)
    private boolean security;

    @Column(nullable = false)
    private boolean messaging;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeploymentTarget deploymentTarget;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GenerationRequestStatus status;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @SuppressWarnings("unused")
    protected GenerationRequestEntity() {
    }

    public GenerationRequestEntity(
            UUID id,
            String name,
            String template,
            boolean database,
            boolean restApi,
            boolean security,
            boolean messaging,
            DeploymentTarget deploymentTarget,
            GenerationRequestStatus status,
            OffsetDateTime createdAt
    ) {
        this.id = id;
        this.name = name;
        this.template = template;
        this.database = database;
        this.restApi = restApi;
        this.security = security;
        this.messaging = messaging;
        this.deploymentTarget = deploymentTarget;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTemplate() {
        return template;
    }

    public boolean isDatabase() {
        return database;
    }

    public boolean isRestApi() {
        return restApi;
    }

    public boolean isSecurity() {
        return security;
    }

    public boolean isMessaging() {
        return messaging;
    }

    public DeploymentTarget getDeploymentTarget() {
        return deploymentTarget;
    }

    public GenerationRequestStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
