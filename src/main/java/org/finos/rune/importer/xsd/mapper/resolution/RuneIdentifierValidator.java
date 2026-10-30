package org.finos.rune.importer.xsd.mapper.resolution;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validates names emitted into Rune declarations and properties.
 *
 * <p>The Rune lexer accepts an ASCII identifier beginning with a letter or
 * underscore and continuing with letters, digits, or underscores. Keeping
 * this check at mapper boundaries prevents a lossy naming normalization from
 * publishing an invalid declaration.</p>
 */
public final class RuneIdentifierValidator {

    private static final Pattern RUNE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private RuneIdentifierValidator() {
    }

    public static String requireValid(String generatedName, String sourceName, String symbol) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        if (generatedName == null || !RUNE_IDENTIFIER.matcher(generatedName).matches()) {
            throw new IllegalStateException(
                    "Invalid Rune " + symbol + " name '" + generatedName
                            + "' generated from XSD name '" + sourceName
                            + "'. Expected an ASCII identifier matching [A-Za-z_][A-Za-z0-9_]*");
        }
        return generatedName;
    }
}
