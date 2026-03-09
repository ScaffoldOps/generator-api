package com.scaffoldops.generatorapi.infrastructure.persistence.repository;

import com.scaffoldops.generatorapi.infrastructure.persistence.entity.GenerationRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataGenerationRequestJpaRepository extends JpaRepository<GenerationRequestEntity, UUID> {
}
