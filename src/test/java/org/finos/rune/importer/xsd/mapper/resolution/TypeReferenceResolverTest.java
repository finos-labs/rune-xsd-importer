package org.finos.rune.importer.xsd.mapper.resolution;

import com.regnosys.rosetta.rosetta.RosettaType;
import com.regnosys.rosetta.rosetta.simple.Data;
import com.regnosys.rosetta.rosetta.simple.SimpleFactory;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.inspector.XsdUtils;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.finos.rune.importer.xsd.registry.RegistryTestUtils;
import org.finos.rune.importer.xsd.registry.RuneTypeRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;

class TypeReferenceResolverTest {

    private static final String NAMESPACE = "http://example.com/rune/test/refs/element";

    @Test
    void preservesQualifiedNativeWrapperElementReference() {
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(Path.of(
                "src/test/resources/org/finos/rune/xsd/integration/features/refs/element-ref.xsd")));
        RuneTypeRegistry registry = new RuneTypeRegistry(RegistryTestUtils.completeBuiltinRegistry());
        Data wrapper = SimpleFactory.eINSTANCE.createData();
        wrapper.setName("GlobalRefEl");
        registry.register(RegistryKey.of(NAMESPACE, "globalRefEl"), wrapper);

        TypeReferenceResolver resolver = new TypeReferenceResolver(
                inspector,
                registry);

        RosettaType resolved = resolver.resolveElementReference(
                Optional.of(RegistryKey.of(NAMESPACE, "globalRefEl")),
                RegistryKey.of("http://www.w3.org/2001/XMLSchema", "string"));

        assertSame(wrapper, resolved);
    }
}
