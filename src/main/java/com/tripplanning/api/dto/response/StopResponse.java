package com.tripplanning.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record StopResponse(
    @Schema(example = "42")
    long id,
    @Schema(example = "Eiffel Tower")
    String locationName,
    @Schema(example = "Paris by night.")
    String description,
    String imageUrl
) {
}
