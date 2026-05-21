package com.tripplanning.externalinfo.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tripplanning.externalinfo.dto.ExternalInfoDtos.AccommodationExternalInfo;
import com.tripplanning.externalinfo.dto.ExternalInfoDtos.Tour;

public final class ViatorPriceSplitter {

    private static final double SIMILAR_PRICE_TOLERANCE = 0.40;
    private static final int MAX_TOURS_PER_TILE = 3;
    private static final Pattern PRICE_PATTERN = Pattern.compile("^([\\d.,]+)\\s+([A-Za-z]{3})$");

    private ViatorPriceSplitter() {}

    public static AccommodationExternalInfo split(List<Tour> tours, BigDecimal accommodationCost, String currency) {
        if (tours == null || tours.isEmpty()) {
            return new AccommodationExternalInfo(List.of(), List.of());
        }
        List<Tour> similar = new ArrayList<>();
        List<Tour> other = new ArrayList<>();
        boolean canMatchPrice =
                accommodationCost != null
                        && accommodationCost.signum() > 0
                        && currency != null
                        && !currency.isBlank();

        for (Tour tour : tours) {
            if (canMatchPrice && isSimilarPrice(tour, accommodationCost, currency)) {
                similar.add(tour);
            } else {
                other.add(tour);
            }
        }

        return new AccommodationExternalInfo(
                similar.stream().limit(MAX_TOURS_PER_TILE).toList(),
                other.stream().limit(MAX_TOURS_PER_TILE).toList());
    }

    private static boolean isSimilarPrice(Tour tour, BigDecimal targetCost, String targetCurrency) {
        ParsedPrice parsed = parsePrice(tour.price());
        if (parsed == null) {
            return false;
        }
        if (!targetCurrency.equalsIgnoreCase(parsed.currency())) {
            return false;
        }
        double target = targetCost.doubleValue();
        if (target <= 0) {
            return false;
        }
        double low = target * (1.0 - SIMILAR_PRICE_TOLERANCE);
        double high = target * (1.0 + SIMILAR_PRICE_TOLERANCE);
        return parsed.amount() >= low && parsed.amount() <= high;
    }

    static ParsedPrice parsePrice(String price) {
        if (price == null || price.isBlank()) {
            return null;
        }
        Matcher matcher = PRICE_PATTERN.matcher(price.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            String amountRaw = matcher.group(1).replace(',', '.');
            double amount = Double.parseDouble(amountRaw);
            String currency = matcher.group(2).toUpperCase(Locale.ROOT);
            return new ParsedPrice(amount, currency);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    record ParsedPrice(double amount, String currency) {}
}
