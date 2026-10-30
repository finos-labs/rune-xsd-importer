package org.finos.rune.importer.xsd.mapper.mapping;

import java.util.List;

/**
 * Immutable enum mapping shared by the Rune declaration and XML
 * serialization-configuration outputs.
 */
public record EnumMapping(String sourceName, List<Value> values) {

    public EnumMapping {
        values = List.copyOf(values);
    }

    /** Immutable value mapping owned by its containing enum mapping. */
    public record Value(String runeLabel, String xmlValue) {
    }
}


