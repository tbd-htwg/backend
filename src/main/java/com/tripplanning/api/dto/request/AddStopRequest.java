package com.tripplanning.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddStopRequest(
    @Schema(example = "Eiffel Tower")
    @NotBlank
    @Size(max = 150)
    String name,

    @Schema(example = "Paris by night.")
    @Size(max = 500)
    String description,

    @Schema(example = "https://mein-cloud-speicher.de/foto123.jpg")
    String imageUrl
) {
}