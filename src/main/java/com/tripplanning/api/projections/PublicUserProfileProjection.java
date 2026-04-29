package com.tripplanning.api.projections;

import org.springframework.data.rest.core.config.Projection;
import org.springframework.beans.factory.annotation.Value;

import com.tripplanning.user.UserEntity;

@Projection(name = "public", types = { UserEntity.class })
public interface PublicUserProfileProjection {
    Long getId();
    String getName();
    String getDescription();
    
    @Value("#{@imageService.createSignedReadUrl(target.imagePath)}")
    String getProfileImageUrl();
}