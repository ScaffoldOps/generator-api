package com.scaffoldops.generatorapi.infrastructure.persistence.repository;

import com.scaffoldops.generatorapi.infrastructure.persistence.entity.GenerationRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SpringDataGenerationRequestJpaRepository extends JpaRepository<GenerationRequestEntity, UUID>,
        JpaSpecificationExecutor<GenerationRequestEntity> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from GenerationRequestEntity r where r.id = :id")
    java.util.Optional<GenerationRequestEntity> findLockedById(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Query(value = """
        SELECT * FROM generation_requests
        WHERE generation_status = 'GENERATION_FAILED'
          AND artifact_ref IS NOT NULL AND trim(artifact_ref) <> ''
          AND (image_ref IS NULL OR trim(image_ref) = '')
          AND failure_stage IN ('IMAGE_BUILD', 'IMAGE_PUSH')
          AND coalesce(retry_count, 0) < :maxRetries
          AND (recovery_reserved_until IS NULL OR recovery_reserved_until < :now)
        ORDER BY updated_at, id LIMIT :batchSize FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    java.util.List<GenerationRequestEntity> findRecoverable(
        @org.springframework.data.repository.query.Param("maxRetries") int maxRetries,
        @org.springframework.data.repository.query.Param("batchSize") int batchSize,
        @org.springframework.data.repository.query.Param("now") java.time.OffsetDateTime now);
}
