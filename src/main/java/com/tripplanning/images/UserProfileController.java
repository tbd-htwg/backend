package com.tripplanning.images;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripplanning.user.UserRepository;
import com.tripplanning.user.UserEntity;


import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v2/users")
@RequiredArgsConstructor
public class UserProfileController {
    private final ImageService imageService;
    private final UserRepository userRepository;

    @PostMapping("/{userId}/images")
    public ResponseEntity<?> createUploadUrl(
            @PathVariable Long userId, 
            @RequestBody ImageUploadDtos.CreateUploadRequest request) {
        try {
            ImageService.SignedUploadInfo signedUpload =
                    imageService.createSignedUpload(
                            "user-profiles/" + userId,
                            request.fileName(),
                            request.contentType());

            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            user.setImageUrl(signedUpload.objectUrl());
            userRepository.save(user);

            return ResponseEntity.ok(
                    new ImageUploadDtos.CreateUploadResponse(
                            signedUpload.uploadUrl(),
                            signedUpload.objectUrl(),
                            signedUpload.objectName(),
                            signedUpload.contentType()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error while creating upload URL: " + e.getMessage());
        }
    }
}