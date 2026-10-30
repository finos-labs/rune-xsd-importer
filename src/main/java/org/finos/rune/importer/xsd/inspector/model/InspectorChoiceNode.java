package org.finos.rune.importer.xsd.inspector.model;

import java.util.List;

/**
 * Represents a reference to a Synthetic Choice Block (Pointer)
 *
 * @param syntheticTypeName
 * @param options Can contain AttributeNode or GroupNode
 * @param min
 * @param max
 */
public record InspectorChoiceNode(
        String syntheticTypeName,
        List<InspectorNode> options,
        int min,
        int max
) implements InspectorNode {}
