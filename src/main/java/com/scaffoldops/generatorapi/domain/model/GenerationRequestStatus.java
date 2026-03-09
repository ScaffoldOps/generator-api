package com.scaffoldops.generatorapi.domain.model;

public enum GenerationRequestStatus {
    RECEIVED,
    GENERATING,
    GENERATED,
    DEPLOYING,
    DEPLOYED,
    FAILED
}
