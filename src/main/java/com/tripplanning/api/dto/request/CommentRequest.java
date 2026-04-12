package com.tripplanning.api.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CommentRequest(
    @Schema(example = "42")
    @Positive
    long userId,
    @Schema(example = "3")
    @Positive
    long tripId,
    @Schema(example = "2026-07-15")
    @NotNull
    LocalDate createdAt,
    @Schema(example = "Looks like an amazing vacation!")
    @NotBlank
    @Size(max = 1000)
    String content
) {
}
