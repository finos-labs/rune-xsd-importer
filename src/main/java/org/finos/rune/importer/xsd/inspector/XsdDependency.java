package org.finos.rune.importer.xsd.inspector;

import java.util.Objects;

/**
 * A required reference discovered while inspecting an XSD declaration.
 *
 * @param source qualified declaration containing the reference
 * @param targetQName resolved Clark QName, or the original unresolved value
 * @param targetRole declaration space expected by the reference
 * @param context human-readable path used in diagnostics
 * @param type semantic type of the dependency
 */
public record XsdDependency(
        XsdDeclaration source,
        String targetQName,
        XsdDeclarationRole targetRole,
        String context,
        XsdDependencyType type) {

    public XsdDependency {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(targetRole, "targetRole must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }
}
