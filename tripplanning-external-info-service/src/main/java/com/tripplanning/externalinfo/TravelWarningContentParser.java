package com.tripplanning.externalinfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a short situational summary from Auswärtiges Amt HTML ({@code content}), skipping
 * site-wide boilerplate that appears at the top of every country page.
 */
public final class TravelWarningContentParser {

    private static final Pattern H2_PATTERN =
            Pattern.compile("<h2[^>]*>(.*?)</h2>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern P_PATTERN =
            Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final List<String> SECTION_PRIORITY =
            List.of(
                    "aktuelles",
                    "sicherheit - reisewarnung",
                    "sicherheit - teilreisewarnung",
                    "sicherheit");

    private static final List<Pattern> BOILERPLATE_PATTERNS =
            List.of(
                    Pattern.compile("Lagen können sich schnell", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("Krisenvorsorgeliste", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("Newsletter", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("Sicher Reisen", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("Weltweiter Sicherheitshinweis", Pattern.CASE_INSENSITIVE),
                    Pattern.compile(
                            "Einreise- und Zollbestimmungen für deutsche Staatsangehörige",
                            Pattern.CASE_INSENSITIVE),
                    Pattern.compile(
                            "Für die direkte Einreise aus Deutschland sind keine Pflichtimpfungen",
                            Pattern.CASE_INSENSITIVE),
                    Pattern.compile(
                            "Hier finden Sie Adressen zuständiger diplomatischer Vertretungen",
                            Pattern.CASE_INSENSITIVE));

    private TravelWarningContentParser() {}

    public static String extractSummary(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Map<String, String> sections = parseH2Sections(html);
        for (String priority : SECTION_PRIORITY) {
            for (Map.Entry<String, String> entry : sections.entrySet()) {
                if (matchesSection(entry.getKey(), priority)) {
                    String paragraph = firstMeaningfulParagraph(entry.getValue());
                    if (!paragraph.isBlank()) {
                        return truncateAtSentenceEnd(paragraph, 280);
                    }
                }
            }
        }
        for (Map.Entry<String, String> entry : sections.entrySet()) {
            if (isSkippedSection(entry.getKey())) {
                continue;
            }
            String paragraph = firstMeaningfulParagraph(entry.getValue());
            if (!paragraph.isBlank()) {
                return truncateAtSentenceEnd(paragraph, 280);
            }
        }
        return "";
    }

    public static String resolveStatus(
            boolean warning, boolean partialWarning, boolean situationWarning, boolean situationPartWarning) {
        if (warning) {
            return situationWarning ? "Reisewarnung (Situation)" : "Reisewarnung";
        }
        if (partialWarning) {
            return situationPartWarning ? "Teilreisewarnung (Situation)" : "Teilreisewarnung";
        }
        return "Sicherheitshinweis";
    }

    private static Map<String, String> parseH2Sections(String html) {
        Map<String, String> sections = new LinkedHashMap<>();
        Matcher matcher = H2_PATTERN.matcher(html);
        List<SectionMarker> markers = new ArrayList<>();
        while (matcher.find()) {
            String title = stripTags(matcher.group(1)).trim().toLowerCase(Locale.GERMAN);
            markers.add(new SectionMarker(title, matcher.start(), matcher.end()));
        }
        for (int i = 0; i < markers.size(); i++) {
            int start = markers.get(i).contentStart();
            int end = i + 1 < markers.size() ? markers.get(i + 1).headingStart() : html.length();
            String block = html.substring(start, end);
            sections.put(markers.get(i).title(), block);
        }
        return sections;
    }

    private static String firstMeaningfulParagraph(String sectionHtml) {
        Matcher matcher = P_PATTERN.matcher(sectionHtml);
        while (matcher.find()) {
            String text = stripTags(matcher.group(1)).replaceAll("\\s+", " ").trim();
            if (text.length() < 40 || isBoilerplate(text)) {
                continue;
            }
            return text;
        }
        return "";
    }

    private static boolean isBoilerplate(String text) {
        for (Pattern pattern : BOILERPLATE_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesSection(String title, String priority) {
        if ("sicherheit".equals(priority)) {
            return "sicherheit".equals(title);
        }
        return title.equals(priority) || title.startsWith(priority);
    }

    private static boolean isSkippedSection(String title) {
        return title.contains("einreise")
                || title.contains("gesundheit")
                || title.contains("reiseinfos")
                || title.contains("natur und klima")
                || title.contains("länderinfos")
                || title.contains("weitere hinweise");
    }

    private static String stripTags(String html) {
        return html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }

    static String truncateAtSentenceEnd(String message, int maxLength) {
        if (message == null || message.length() <= maxLength) {
            return message == null ? "" : message;
        }
        String sub = message.substring(0, maxLength);
        int lastDot = sub.lastIndexOf('.');
        if (lastDot > 50) {
            return sub.substring(0, lastDot + 1);
        }
        int lastSpace = sub.lastIndexOf(' ');
        return (lastSpace > 0 ? sub.substring(0, lastSpace) : sub) + "...";
    }

    private record SectionMarker(String title, int headingStart, int contentStart) {}
}
