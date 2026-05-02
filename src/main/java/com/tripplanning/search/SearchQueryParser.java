package com.tripplanning.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Parses space-separated {@code q} into facets ({@code transport:train}) and free text. Values must
 * not contain whitespace (v1).
 */
@Component
public class SearchQueryParser {

    /**
     * Known facet keys (before colon), case-insensitive; value is non-whitespace, non-colon run.
     */
    private static final Pattern FACET_TOKEN = Pattern.compile(
            "(?i)^(transport|location|accommodation|accomodation|destination|user|author):([^:\\s]+)$");

    private static final Pattern TRAILING_WHITESPACE = Pattern.compile("\\s+$");

    public ParsedSearchQuery parse(String q) {
        if (q == null || q.isBlank()) {
            return ParsedSearchQuery.empty();
        }

        Matcher trailingMatcher = TRAILING_WHITESPACE.matcher(q);
        String trailingWs = "";
        int coreEnd = q.length();
        if (trailingMatcher.find()) {
            trailingWs = trailingMatcher.group();
            coreEnd = trailingMatcher.start();
        }
        String core = q.substring(0, coreEnd);
        String trimmedCore = core.trim();
        if (trimmedCore.isEmpty()) {
            return new ParsedSearchQuery(List.of(), q);
        }

        List<SearchFacet> facets = new ArrayList<>();
        List<String> freeParts = new ArrayList<>();
        for (String token : trimmedCore.split("\\s+")) {
            Matcher m = FACET_TOKEN.matcher(token);
            if (m.matches()) {
                SearchFacetKey key =
                        SearchFacetKey.fromQueryKey(m.group(1)).orElseThrow();
                facets.add(new SearchFacet(key, m.group(2).toLowerCase(Locale.ROOT)));
            } else {
                freeParts.add(token);
            }
        }
        String freeCore = String.join(" ", freeParts);
        String freeText = freeCore.isEmpty() ? trailingWs : freeCore + trailingWs;
        return new ParsedSearchQuery(List.copyOf(facets), freeText);
    }
}
