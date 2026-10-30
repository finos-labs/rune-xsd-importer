package org.finos.rune.importer.xsd.builder.serialization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XsdUnionIntrospectorTest {

    @Test
    void modelQualifiedNameRemovesNestedAndImplementationSuffixes() {
        assertEquals(
                GeneratedType.class.getName(),
                XsdUnionIntrospector.modelQualifiedName(GeneratedTypeImpl.Nested.class));
    }
}

class GeneratedType {
}

class GeneratedTypeImpl {
    static class Nested {
    }
}
