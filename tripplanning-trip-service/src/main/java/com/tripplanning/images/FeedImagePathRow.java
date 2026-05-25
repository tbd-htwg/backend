package com.tripplanning.images;

/**
 * Ordered image path for feed carousel signing (trip → location → image id).
 */
public record FeedImagePathRow(long tripId, long tripLocationId, long imageId, String imagePath) {}
