package com.scaffoldops.generatorapi.application.service;

import com.scaffoldops.generatorapi.application.port.out.GenerationRequestEventPublisher;
import com.scaffoldops.generatorapi.domain.model.*;
import com.scaffoldops.generatorapi.infrastructure.persistence.adapter.JpaGenerationRequestRepositoryAdapter;
import com.scaffoldops.generatorapi.infrastructure.persistence.entity.GenerationRequestEntity;
import com.scaffoldops.generatorapi.infrastructure.persistence.mapper.GenerationRequestPersistenceMapper;
import com.scaffoldops.generatorapi.infrastructure.persistence.repository.SpringDataGenerationRequestJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({GenerationRequestService.class, JpaGenerationRequestRepositoryAdapter.class,
        GenerationRequestPersistenceMapper.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeleteGenerationRequestTransactionTest {
    @Autowired GenerationRequestService service;
    @Autowired SpringDataGenerationRequestJpaRepository repository;
    @MockitoBean GenerationRequestEventPublisher publisher;

    @Test
    void shouldRollBackDeletionWhenPublicationFailsAndCommitWhenAccepted() {
        UUID id = UUID.randomUUID();
        var now = OffsetDateTime.now();
        repository.saveAndFlush(new GenerationRequestEntity(id, "billing-" + id, "spring",
                true, true, false, false, DeploymentTarget.KUBERNETES, GenerationStatus.GENERATED,
                DeploymentStatus.NOT_DEPLOYED, "{}", null, "s3://bucket/project.zip",
                "docker.io/owner/repo:tag", "scaffoldops-dev", now, now));
        try {
            doThrow(new IllegalStateException("broker unavailable")).when(publisher).publishArtifactCleanupRequested(any());
            assertThatThrownBy(() -> service.deleteById(id)).isInstanceOf(IllegalStateException.class);
            assertThat(repository.findById(id)).isPresent();
            doNothing().when(publisher).publishArtifactCleanupRequested(any());
            assertThat(service.deleteById(id)).isTrue();
            assertThat(repository.findById(id)).isEmpty();
        } finally { repository.deleteById(id); }
    }
}
