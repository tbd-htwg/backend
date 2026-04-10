package com.tripplanning.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record CommentResponse(
    @Schema(example = "42")
    long comment_id,
    @Schema(example = "MaxMustermann")
    String authorName,
    @Schema(example = "Looks like an amazing vacation!")
    String content,
    LocalDateTime createdAt
) {
}