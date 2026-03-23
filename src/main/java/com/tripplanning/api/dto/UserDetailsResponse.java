package com.tripplanning.api.dto;

import java.util.List;

public record UserDetailsResponse(
    Long id,
    String email,
    String name,
    List<TripListItemResponse> trips
) {
}
