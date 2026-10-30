package org.finos.rune.importer.xsd.inspector;

import org.finos.rune.importer.xsd.security.XsdParserJaxpFactory;
import org.xmlet.xsdparser.core.XsdParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utilities for parsing XSD schemas using xmlet's {@link XsdParser} in tests.
 *
 * <p>xmlet parses eagerly in the {@link XsdParser} constructor and only accepts
 * file paths. String inputs are therefore written to temporary .xsd files before
 * parsing.</p>
 */
public final class XsdUtils {

    private static final String TEMP_PREFIX = "rune-test-";
    private static final String TEMP_SUFFIX = ".xsd";

    private XsdUtils() {
    }

    /**
     * Parse an XSD provided as a String.
     */
    public static XsdParser parseXsd(String xsd) {
        return parseXsd(writeTempXsd(xsd));
    }

    /**
     * Parse an XSD provided as a {@link Path}.
     */
    public static XsdParser parseXsd(Path xsdPath) {
        return XsdParserJaxpFactory.defaultFactory().createParser(xsdPath);
    }

    /**
     * Writes the provided XSD content to a temporary file and returns the path.
     */
    public static Path writeTempXsd(String xsd) {
        try {
            Path tempXsd = Files.createTempFile(TEMP_PREFIX, TEMP_SUFFIX);
            Files.writeString(tempXsd, xsd, StandardCharsets.UTF_8);
            tempXsd.toFile().deleteOnExit();
            return tempXsd;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write temporary XSD file", e);
        }
    }
}
