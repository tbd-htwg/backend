package com.tripplanning.common.client;

import java.util.Collection;
import java.util.Map;

import com.tripplanning.common.internal.InternalUserDto;

public interface TripServiceClient {

    boolean tripExists(long tripId);

    Map<Long, InternalUserDto> getUsersByIds(Collection<Long> userIds);

    void evictLikedByFeedCache();

    /** Returns the trip owner's user id, or empty if the trip does not exist. */
    java.util.Optional<Long> getTripOwnerUserId(long tripId);

    boolean isTripOwnedBy(long tripId, long userId);
}
