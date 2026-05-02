package com.tripplanning.trip.read;

import org.springframework.data.rest.core.annotation.HandleAfterCreate;
import org.springframework.data.rest.core.annotation.HandleAfterDelete;
import org.springframework.data.rest.core.annotation.HandleAfterLinkDelete;
import org.springframework.data.rest.core.annotation.HandleAfterLinkSave;
import org.springframework.data.rest.core.annotation.HandleAfterSave;
import org.springframework.data.rest.core.annotation.RepositoryEventHandler;
import org.springframework.stereotype.Component;

import com.tripplanning.accommodation.AccomEntity;
import com.tripplanning.transport.TransportEntity;
import com.tripplanning.trip.TripEntity;
import com.tripplanning.tripLocation.TripLocationEntity;

import lombok.RequiredArgsConstructor;

/**
 * Spring Data REST event handlers that invalidate {@link TripCacheEvictor} caches whenever a trip
 * (or anything embedded in a trip's feed/detail payload) changes through the SDR collections.
 *
 * <p>Mutations made through custom controllers ({@link com.tripplanning.images.TripLocationImageController},
 * {@link com.tripplanning.social.LikeController}) call into {@link TripCacheEvictor} directly.
 */
@Component
@RepositoryEventHandler
@RequiredArgsConstructor
public class TripWriteEvents {

    private final TripCacheEvictor evictor;

    @HandleAfterCreate
    public void afterCreateTrip(TripEntity trip) {
        evictor.evictForTripChange(trip.getId());
    }

    @HandleAfterSave
    public void afterSaveTrip(TripEntity trip) {
        evictor.evictForTripChange(trip.getId());
    }

    @HandleAfterDelete
    public void afterDeleteTrip(TripEntity trip) {
        evictor.evictForTripChange(trip.getId());
    }

    @HandleAfterLinkSave
    public void afterTripLinkSave(TripEntity trip, Object linked) {
        evictor.evictForTripChange(trip.getId());
    }

    @HandleAfterLinkDelete
    public void afterTripLinkDelete(TripEntity trip, Object linked) {
        evictor.evictForTripChange(trip.getId());
    }

    @HandleAfterCreate
    public void afterCreateTripLocation(TripLocationEntity tl) {
        if (tl.getTrip() != null) {
            evictor.evictForTripChange(tl.getTrip().getId());
        } else {
            evictor.evictAllFeeds();
        }
    }

    @HandleAfterSave
    public void afterSaveTripLocation(TripLocationEntity tl) {
        if (tl.getTrip() != null) {
            evictor.evictForTripChange(tl.getTrip().getId());
        } else {
            evictor.evictAllFeeds();
        }
    }

    @HandleAfterDelete
    public void afterDeleteTripLocation(TripLocationEntity tl) {
        if (tl.getTrip() != null) {
            evictor.evictForTripChange(tl.getTrip().getId());
        } else {
            evictor.evictAllFeeds();
        }
    }

    /**
     * Accommodations and transports are shared lookups; the trip rows that reference them are not
     * known here without an extra query. Clearing every feed/detail page is cheaper than tracking
     * individual references and the catalog mutates rarely.
     */
    @HandleAfterCreate
    public void afterCreateAccom(AccomEntity ignored) {
        evictor.evictAllFeeds();
    }

    @HandleAfterSave
    public void afterSaveAccom(AccomEntity ignored) {
        evictor.evictAllFeeds();
    }

    @HandleAfterDelete
    public void afterDeleteAccom(AccomEntity ignored) {
        evictor.evictAllFeeds();
    }

    @HandleAfterCreate
    public void afterCreateTransport(TransportEntity ignored) {
        evictor.evictAllFeeds();
    }

    @HandleAfterSave
    public void afterSaveTransport(TransportEntity ignored) {
        evictor.evictAllFeeds();
    }

    @HandleAfterDelete
    public void afterDeleteTransport(TransportEntity ignored) {
        evictor.evictAllFeeds();
    }
}
