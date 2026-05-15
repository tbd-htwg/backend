package com.tripplanning.common.client;

import java.util.List;

public interface SocialServiceClient {

    List<Long> getLikedTripIdsForUser(long userId);
}
