package com.tripplanning.images;

public final class ImageUploadDtos {
    private ImageUploadDtos() {}

    public record CreateUploadRequest(String fileName, String contentType) {}

    public record CreateUploadResponse(
            String uploadUrl,
            String objectUrl,
            String objectName,
            String contentType) {}
}
