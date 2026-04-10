package com.tripplanning.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CommentRequest(
    @Schema(example = "42")
    @Positive
    long userId,
    @Schema(example = "Looks like an amazing vacation!")
    @NotBlank
    @Size(max = 1000)
    String content
) {
}
