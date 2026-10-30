package org.finos.rune.importer.xsd.inspector.model;

import java.util.List;

public record InspectorSimpleTypeNode(
        String baseTypeQName,
        List<String> patterns,
        Integer minLength,
        Integer maxLength,
        // Numeric facets are retained for mapper-side restriction handling.
        String minInclusive,
        String maxInclusive,
        Integer totalDigits,
        Integer fractionDigits
) {}
