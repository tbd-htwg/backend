package com.tripplanning.images;

import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final Storage storage; 
    @Value("${spring.cloud.gcp.storage.bucket-name}")    
    private String bucketName;

    public String uploadImage(MultipartFile file, String folder) throws IOException {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files (JPG, PNG) allowed!");
        }
        
        String fileName = folder + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
        
        BlobId blobId = BlobId.of(bucketName, fileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType())
                .build();

        storage.create(blobInfo, file.getBytes());
        
        return String.format("https://storage.googleapis.com/%s/%s", bucketName, fileName);
    }
}