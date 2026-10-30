package org.finos.rune.importer.xsd.inspector;

import org.finos.rune.importer.xsd.registry.RegistryKey;

import java.util.Objects;

/**
 * A qualified XSD declaration and its declaration space.
 */
public record XsdDeclaration(
        RegistryKey key,
        XsdDeclarationRole role) {

    public XsdDeclaration {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(role, "role must not be null");
    }

    @Override
    public String toString() {
        return key + " [" + role + "]";
    }
}
