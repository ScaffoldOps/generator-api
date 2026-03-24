package com.scaffoldops.generatorapi.infrastructure.persistence.adapter;

import com.scaffoldops.generatorapi.application.model.GenerationRequestFilters;
import com.scaffoldops.generatorapi.application.port.out.GenerationRequestRepository;
import com.scaffoldops.generatorapi.domain.model.GenerationRequest;
import com.scaffoldops.generatorapi.infrastructure.persistence.entity.GenerationRequestEntity;
import com.scaffoldops.generatorapi.infrastructure.persistence.mapper.GenerationRequestPersistenceMapper;
import com.scaffoldops.generatorapi.infrastructure.persistence.repository.SpringDataGenerationRequestJpaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
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
    public List<GenerationRequest> findAllByFilters(GenerationRequestFilters filters) {
        return repository.findAll(buildSpecification(filters), Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(mapper::toDomain)
                .toList();
    }

    private Specification<GenerationRequestEntity> buildSpecification(GenerationRequestFilters filters) {
        GenerationRequestFilters effectiveFilters = filters == null ? GenerationRequestFilters.empty() : filters;
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                equalIfPresent(criteriaBuilder, root.get("name"), effectiveFilters.name()),
                equalIfPresent(criteriaBuilder, root.get("template"), effectiveFilters.template()),
                equalIfPresent(criteriaBuilder, root.get("status"), effectiveFilters.status()),
                equalIfPresent(criteriaBuilder, root.get("deploymentTarget"), effectiveFilters.deploymentTarget()),
                equalIfPresent(criteriaBuilder, root.get("database"), effectiveFilters.database()),
                equalIfPresent(criteriaBuilder, root.get("restApi"), effectiveFilters.restApi()),
                equalIfPresent(criteriaBuilder, root.get("security"), effectiveFilters.security()),
                equalIfPresent(criteriaBuilder, root.get("messaging"), effectiveFilters.messaging())
        );
    }

    private <T> Predicate equalIfPresent(CriteriaBuilder criteriaBuilder, Path<T> path, T value) {
        return value == null ? criteriaBuilder.conjunction() : criteriaBuilder.equal(path, value);
    }
}
