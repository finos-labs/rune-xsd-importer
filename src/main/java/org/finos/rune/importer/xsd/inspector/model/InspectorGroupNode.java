package org.finos.rune.importer.xsd.inspector.model;

/**
 * Represents a reference to a Global Group (Pointer)
 * @param groupName
 * @param groupTypeQName
 * @param min
 * @param max
 * @param documentation
 */
public record InspectorGroupNode(
        String groupName,
        String groupTypeQName,
        int min,
        int max,
        String documentation
) implements InspectorNode {}
