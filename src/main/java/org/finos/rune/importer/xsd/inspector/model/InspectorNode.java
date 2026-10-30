package org.finos.rune.importer.xsd.inspector.model;

public sealed interface InspectorNode permits
        InspectorAttributeNode,
        InspectorGroupNode,
        InspectorChoiceNode {
}
