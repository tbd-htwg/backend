package com.tripplanning.api.dto.response;

public record AccomResponse(
    long id,
    String name,
    String type,
    String address
) {}