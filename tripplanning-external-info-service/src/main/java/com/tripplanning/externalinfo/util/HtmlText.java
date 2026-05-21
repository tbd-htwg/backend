package com.tripplanning.externalinfo.util;

public final class HtmlText {

    private HtmlText() {}

    /** Removes HTML tags and collapses whitespace for safe plain-text UI. */
    public static String strip(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }
}
