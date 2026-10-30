package org.finos.rune.importer.xsd.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XsdImporterConfigTest {

    @Test
    void defaultsToRune() {
        assertEquals(
                XsdImporterConfig.DEFAULT_MODEL_FILE_EXTENSION,
                XsdImporterConfig.defaults().getModelFileExtension());
        assertEquals(
                XsdImporterConfig.DEFAULT_MODEL_FILE_EXTENSION,
                XsdImporterConfig.builder().build().getModelFileExtension());
    }

    @Test
    void acceptsBothSupportedExtensionsAndNormalizesThem() {
        assertEquals(
                XsdImporterConfig.RUNE_MODEL_FILE_EXTENSION,
                XsdImporterConfig.builder().modelFileExtension(" RUNE ").build().getModelFileExtension());
        assertEquals(
                XsdImporterConfig.ROSETTA_MODEL_FILE_EXTENSION,
                XsdImporterConfig.builder().modelFileExtension("ROSETTA").build().getModelFileExtension());
        assertEquals(
                XsdImporterConfig.ROSETTA_MODEL_FILE_EXTENSION,
                XsdImporterConfig.builder().modelFileExtension(".ROSETTA").build().getModelFileExtension());
    }

    @Test
    void rejectsNullBlankAndUnsupportedExtensions() {
        assertThrows(IllegalArgumentException.class,
                () -> XsdImporterConfig.builder().modelFileExtension(null));
        assertThrows(IllegalArgumentException.class,
                () -> XsdImporterConfig.builder().modelFileExtension("   "));
        assertThrows(IllegalArgumentException.class,
                () -> XsdImporterConfig.builder().modelFileExtension(".txt"));
        assertThrows(IllegalArgumentException.class,
                () -> XsdImporterConfig.builder().modelFileExtension(".rune.backup"));
    }

    @Test
    void builderCanBeReusedWithoutChangingBuiltConfigurations() {
        XsdImporterConfig.Builder builder = XsdImporterConfig.builder();
        XsdImporterConfig rune = builder.build();

        builder.modelFileExtension("rosetta");
        XsdImporterConfig rosetta = builder.build();

        assertEquals(XsdImporterConfig.RUNE_MODEL_FILE_EXTENSION, rune.getModelFileExtension());
        assertEquals(XsdImporterConfig.ROSETTA_MODEL_FILE_EXTENSION, rosetta.getModelFileExtension());
    }
}
