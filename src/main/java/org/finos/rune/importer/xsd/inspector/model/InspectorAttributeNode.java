package org.finos.rune.importer.xsd.inspector.model;

import com.rosetta.util.serialisation.AttributeXMLRepresentation;

import java.util.Optional;

/**
 * Lightweight record representing a flattened member (Attribute or Element) of a ComplexType.
 * Represents a standard field (Leaf)
 *
 * @param name
 * @param typeQName
 * @param min
 * @param max
 * @param documentation
 * @param xmlNamespace namespace URI of the XML member, or an empty string for
 *                    an unqualified local attribute
 */
public record InspectorAttributeNode(
        String name,
        String typeQName,
        int min,
        int max,
        String documentation,
        String xmlNamespace,
        AttributeXMLRepresentation xmlRepresentation,
        Optional<String> elementRefUri
) implements InspectorNode {}
