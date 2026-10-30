package org.finos.rune.importer.xsd.core;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuneModelCategoryTest {

    @Test
    void retainsTheExpectedLowercaseCategoryNames() {
        assertEquals("type", RuneModelCategory.TYPE.toString());
        assertEquals("enum", RuneModelCategory.ENUM.toString());
    }

    @Test
    void categoryNamesAreIndependentOfTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertEquals("type", RuneModelCategory.TYPE.toString());
            assertEquals("enum", RuneModelCategory.ENUM.toString());
        } finally {
            Locale.setDefault(previous);
        }
    }
}
