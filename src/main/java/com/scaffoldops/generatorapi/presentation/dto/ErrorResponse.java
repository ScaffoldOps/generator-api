package com.scaffoldops.generatorapi.presentation.dto;

import java.time.OffsetDateTime;

public record ErrorResponse(
        String message,
        OffsetDateTime timestamp
) {
}
