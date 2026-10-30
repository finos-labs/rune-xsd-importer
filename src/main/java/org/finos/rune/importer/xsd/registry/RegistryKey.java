package org.finos.rune.importer.xsd.registry;

import java.util.Objects;

/**
 * A composite key representing a fully qualified XSD type or element.
 * <p>
 * Format: <code>{namespaceURI}localName</code>
 */
public record RegistryKey(String namespace, String localName) {

    public RegistryKey {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(localName, "localName must not be null");
    }

    /**
     * Parses a string representation into a RegistryKey.
     * Automatically identifies if the input is an FQN (starts with '{') or a URL.
     *
     * @param value The string representation.
     * @return The RegistryKey, or null if the format is invalid.
     */
    public static RegistryKey parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        if (value.startsWith("{")) {
            int closeBrace = value.indexOf('}');
            if (closeBrace < 2) {
                return null;
            }

            String namespace = value.substring(1, closeBrace);
            String localName = value.substring(closeBrace + 1);
            return namespace.isBlank() || localName.isBlank()
                    ? null
                    : new RegistryKey(namespace, localName);
        } else if (value.contains("/")) {
            int lastSlash = value.lastIndexOf('/');
            if (lastSlash == value.length() - 1) {
                return null;
            }

            String namespace = value.substring(0, lastSlash);
            String localName = value.substring(lastSlash + 1);
            return namespace.isBlank() || localName.isBlank()
                    ? null
                    : new RegistryKey(namespace, localName);
        }

        return null; // Unrecognized format
    }

    /**
     * Helper to create a key from a known namespace and local name.
     */
    public static RegistryKey of(String namespace, String localName) {
        return new RegistryKey(namespace, localName);
    }

    @Override
    public String toString() {
        return "{" + namespace + "}" + localName;
    }
}
