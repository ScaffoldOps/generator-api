package com.scaffoldops.generatorapi.domain.model;

public enum DeploymentStatus {
    NOT_DEPLOYED,
    DEPLOYMENT_REQUESTED,
    DEPLOYING,
    DEPLOYED,
    UNDEPLOYMENT_REQUESTED,
    UNDEPLOYING,
    DEPLOYMENT_FAILED
}
