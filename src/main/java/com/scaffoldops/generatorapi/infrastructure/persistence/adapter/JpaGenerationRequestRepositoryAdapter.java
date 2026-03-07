package com.scaffoldops.generatorapi.infrastructure.persistence.adapter;

import com.scaffoldops.generatorapi.application.port.out.GenerationRequestRepository;
import com.scaffoldops.generatorapi.domain.model.GenerationRequest;
import com.scaffoldops.generatorapi.infrastructure.persistence.mapper.GenerationRequestPersistenceMapper;
import com.scaffoldops.generatorapi.infrastructure.persistence.repository.SpringDataGenerationRequestJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaGenerationRequestRepositoryAdapter implements GenerationRequestRepository {

    private final SpringDataGenerationRequestJpaRepository repository;
    private final GenerationRequestPersistenceMapper mapper;

    public JpaGenerationRequestRepositoryAdapter(
            SpringDataGenerationRequestJpaRepository repository,
            GenerationRequestPersistenceMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public GenerationRequest save(GenerationRequest generationRequest) {
        return mapper.toDomain(repository.save(mapper.toEntity(generationRequest)));
    }

    @Override
    public Optional<GenerationRequest> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<GenerationRequest> findAll() {
        return repository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }
}
