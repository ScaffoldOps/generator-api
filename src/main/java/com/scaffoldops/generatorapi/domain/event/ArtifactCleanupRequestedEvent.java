package com.scaffoldops.generatorapi.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ArtifactCleanupRequestedEvent(
        UUID requestId,
        String name,
        OffsetDateTime deletedAt
) {
}
