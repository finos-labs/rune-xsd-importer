package org.finos.rune.importer.xsd.mapper.resolution;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuneIdentifierNamingUtilsTest {

    @Test
    void preservesExistingRuneNamingCompatibility() {
        assertEquals("TradeHeader", RuneIdentifierNamingUtils.toTypeCase("trade-header"));
        assertEquals("tradeHeader", RuneIdentifierNamingUtils.toAttributeCase("TradeHeader"));
        assertEquals("_count", RuneIdentifierNamingUtils.toAttributeCase("count"));
        assertEquals("_1", RuneIdentifierNamingUtils.toEnumLabel("1"));
    }

    @Test
    void exposesNormalizationCollisionsThatBoundariesMustReject() {
        assertEquals("AB", RuneIdentifierNamingUtils.toTypeCase("a-b"));
        assertEquals("AB", RuneIdentifierNamingUtils.toTypeCase("a_b"));
        assertEquals("AB", RuneIdentifierNamingUtils.toTypeCase("a.b"));
        assertEquals("aB", RuneIdentifierNamingUtils.toAttributeCase("a-b"));
        assertEquals("aB", RuneIdentifierNamingUtils.toAttributeCase("a_b"));
        assertEquals("aB", RuneIdentifierNamingUtils.toAttributeCase("a.b"));
    }

    @Test
    void characterizesValidUnicodeXsdNamesAndEnumValues() {
        assertEquals("Clair", RuneIdentifierNamingUtils.toTypeCase("éclair"));
        assertEquals("Clair", RuneIdentifierNamingUtils.toAttributeCase("éclair"));
        assertEquals("", RuneIdentifierNamingUtils.toTypeCase("雪"));
        assertEquals("", RuneIdentifierNamingUtils.toAttributeCase("雪"));
        assertEquals("_", RuneIdentifierNamingUtils.toEnumLabel("é"));
        assertEquals("E_", RuneIdentifierNamingUtils.toEnumLabel("e\u0301"));
        assertEquals("A__", RuneIdentifierNamingUtils.toEnumLabel("a-雪"));
    }

    @Test
    void preservesAlreadySanitizedAndReservedInputCompatibility() {
        assertEquals("TradeHeader", RuneIdentifierNamingUtils.toTypeCase("TradeHeader"));
        assertEquals("tradeHeader", RuneIdentifierNamingUtils.toAttributeCase("tradeHeader"));
        assertEquals("VALUE_1", RuneIdentifierNamingUtils.toEnumLabel("VALUE_1"));
        assertEquals("_type", RuneIdentifierNamingUtils.toAttributeCase("type"));
        assertEquals("_count", RuneIdentifierNamingUtils.toAttributeCase("count"));
        assertEquals("oneOf", RuneIdentifierNamingUtils.toAttributeCase("one-of"));
    }

    @Test
    void normalizationIsIdempotentForSupportedRepresentativeValues() {
        List<String> values = List.of("trade-header", "TradeHeader", "a_b", "one-of");

        for (String value : values) {
            String type = RuneIdentifierNamingUtils.toTypeCase(value);
            String attribute = RuneIdentifierNamingUtils.toAttributeCase(value);
            String label = RuneIdentifierNamingUtils.toEnumLabel(value);

            assertEquals(type, RuneIdentifierNamingUtils.toTypeCase(type), "type: " + value);
            assertEquals(attribute, RuneIdentifierNamingUtils.toAttributeCase(attribute), "attribute: " + value);
            assertEquals(label, RuneIdentifierNamingUtils.toEnumLabel(label), "enum: " + value);
        }

        assertEquals("Count", RuneIdentifierNamingUtils.toAttributeCase(
                RuneIdentifierNamingUtils.toAttributeCase("count")));
    }

    @Test
    void enumLabelsAreIndependentOfDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            String us = RuneIdentifierNamingUtils.toEnumLabel("i");

            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            String turkish = RuneIdentifierNamingUtils.toEnumLabel("i");

            Locale.setDefault(Locale.ROOT);
            String root = RuneIdentifierNamingUtils.toEnumLabel("i");

            assertEquals("I", us);
            assertEquals(us, turkish);
            assertEquals(us, root);
        } finally {
            Locale.setDefault(original);
        }
    }
}
