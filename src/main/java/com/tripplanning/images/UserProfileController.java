package com.tripplanning.images;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    public ResponseEntity<?> uploadImage(
            @PathVariable Long userId, 
            @RequestParam("file") MultipartFile file) {
        try {
            String url = imageService.uploadImage(file, "user-profiles/" + userId);

            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            user.setImageUrl(url);
            userRepository.save(user);

            return ResponseEntity.ok(url);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error while uploading: " + e.getMessage());
        }
    }
}