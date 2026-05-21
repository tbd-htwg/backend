package com.tripplanning.externalinfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TravelWarningContentParserTest {

    @Test
    void extractSummary_usesAktuellesForReisewarnung() {
        String html =
                """
                <p>Lagen können sich schnell verändern. Krisenvorsorgeliste.</p>
                <h2>Aktuelles</h2>
                <p>Vor Reisen nach Haiti wird gewarnt. Das Land sollte verlassen werden.</p>
                <h2>Einreise und Zoll</h2>
                <p>Einreise- und Zollbestimmungen für deutsche Staatsangehörige können sich ändern.</p>
                """;
        String summary = TravelWarningContentParser.extractSummary(html);
        assertTrue(summary.contains("Haiti"));
        assertTrue(summary.contains("gewarnt"));
    }

    @Test
    void extractSummary_usesSicherheitWhenNoAktuelles() {
        String html =
                """
                <p>Lagen können sich schnell verändern und entwickeln. Wir empfehlen Ihnen Newsletter.</p>
                <h2>Sicherheit</h2>
                <p>In den vergangenen Jahren wurden wiederholt terroristische Anschläge in den USA verübt.</p>
                """;
        String summary = TravelWarningContentParser.extractSummary(html);
        assertTrue(summary.contains("terroristische Anschläge"));
    }

    @Test
    void resolveStatus_mapsWarningLevels() {
        assertEquals("Reisewarnung", TravelWarningContentParser.resolveStatus(true, false, false, false));
        assertEquals(
                "Teilreisewarnung",
                TravelWarningContentParser.resolveStatus(false, true, false, false));
        assertEquals(
                "Sicherheitshinweis",
                TravelWarningContentParser.resolveStatus(false, false, false, false));
    }
}
