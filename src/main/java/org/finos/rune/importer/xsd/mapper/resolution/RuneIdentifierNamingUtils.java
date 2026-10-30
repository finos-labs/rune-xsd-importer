package org.finos.rune.importer.xsd.mapper.resolution;

import com.google.common.base.CaseFormat;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class RuneIdentifierNamingUtils {

    private static final Pattern SANITIZER = Pattern.compile("[^a-zA-Z0-9]");

    private static final Set<String> KEYWORDS = Set.of(
            "type", "func", "enum", "alias", "data", "namespace", "version", "import",
            "condition", "rule", "validate", "meta",
            "int", "number", "string", "boolean", "date", "time", "zonedDateTime",
            "count", "keys", "values", "empty", "absent", "exists", "choice"
    );

    private RuneIdentifierNamingUtils() {
    }

    public static String toTypeCase(String xsdName) {
        if (xsdName == null || xsdName.isEmpty()) return xsdName;

        String clean = SANITIZER.matcher(xsdName).replaceAll("-");

        if (clean.contains("-")) {
            return CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, clean);
        }

        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    /**
     * Returns whether two already-generated Rune type names identify the same
     * model-builder symbol. This is deliberately a target-name comparison; it
     * must not be used to resolve XSD declarations or registry keys.
     */
    public static boolean targetTypeNamesCollide(String first, String second) {
        return first != null && first.equalsIgnoreCase(second);
    }

    public static String toAttributeCase(String xsdName) {
        if (xsdName == null || xsdName.isEmpty()) return xsdName;

        String clean = SANITIZER.matcher(xsdName).replaceAll("-");
        String result;

        if (clean.contains("-")) {
            result = CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, clean);
        } else {
            result = Character.toLowerCase(clean.charAt(0)) + clean.substring(1);
        }

        if (KEYWORDS.contains(result)) {
            return "_" + result;
        }

        return result;
    }

    public static String toEnumLabel(String xsdValue) {
        if (xsdValue == null || xsdValue.isEmpty()) return xsdValue;

        String clean = SANITIZER.matcher(xsdValue).replaceAll("_");

        if (Character.isDigit(clean.charAt(0))) {
            clean = "_" + clean;
        }

        return clean.toUpperCase(Locale.ROOT);
    }

}
