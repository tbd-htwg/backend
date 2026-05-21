package com.tripplanning.externalinfo.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tripplanning.externalinfo.dto.ExternalInfoDtos.AccommodationExternalInfo;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.Tour;

class ViatorPriceSplitterTest {

    @Test
    void split_putsMatchingCurrencyAndPriceInSimilarBucket() {
        List<Tour> tours =
                List.of(
                        new Tour("1", "Budget tour", "90.00 EUR", "https://a"),
                        new Tour("2", "Premium tour", "200.00 EUR", "https://b"),
                        new Tour("3", "Mid tour", "105.00 EUR", "https://c"));

        AccommodationExternalInfo result =
                ViatorPriceSplitter.split(tours, new BigDecimal("100.00"), "EUR");

        assertEquals(2, result.similarPriceTours().size());
        assertTrue(result.similarPriceTours().stream().anyMatch(t -> t.id().equals("1")));
        assertTrue(result.similarPriceTours().stream().anyMatch(t -> t.id().equals("3")));
        assertEquals(1, result.otherTours().size());
        assertEquals("2", result.otherTours().get(0).id());
    }

    @Test
    void split_withoutCost_putsAllInOther() {
        List<Tour> tours = List.of(new Tour("1", "Tour", "50.00 EUR", "https://a"));

        AccommodationExternalInfo result = ViatorPriceSplitter.split(tours, null, "EUR");

        assertTrue(result.similarPriceTours().isEmpty());
        assertEquals(1, result.otherTours().size());
    }
}
