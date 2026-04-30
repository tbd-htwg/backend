package com.tripplanning.images;

public final class ImageUploadDtos {
    private ImageUploadDtos() {}

    public record CreateUploadRequest(String fileName, String contentType) {}

    public record CreateUploadResponse(
            Long imageId,
            String uploadUrl, //signed upload URL (time limited)
            String signedReadUrl, //signed read URL (time limited)
            String objectName, //image path in database
            String contentType) {}
}
