package org.finos.rune.importer.xsd.registry;

import com.regnosys.rosetta.rosetta.RosettaFactory;
import com.regnosys.rosetta.rosetta.RosettaType;
import com.rosetta.model.lib.ModelSymbolId;
import com.rosetta.util.serialisation.TypeXMLConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RuneTypeRegistryTest {

    @Test
    void rejectsNullBuiltinRegistryDuringConstruction() {
        assertThrows(NullPointerException.class, () -> new RuneTypeRegistry(null));
    }

    @Test
    void rejectsReplacingARegisteredSourceType() {
        RuneTypeRegistry registry = registry();
        RegistryKey key = RegistryKey.of("http://example.com/source", "Foo");
        RosettaType first = type("Foo");
        RosettaType second = type("FooReplacement");

        registry.register(key, first);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> registry.register(key, second));

        assertTrue(failure.getMessage().contains(key.toString()));
    }

    @Test
    void allowsIdempotentReRegistrationOfTheSameSourceType() {
        RuneTypeRegistry registry = registry();
        RegistryKey key = RegistryKey.of("http://example.com/source", "Foo");
        RosettaType type = type("Foo");

        registry.register(key, type);
        registry.register(key, type);

        assertSame(type, registry.resolve(key));
    }

    @Test
    void userTypesTakePrecedenceOverBuiltinTypesForTheSameQualifiedKey() {
        RuneTypeRegistry registry = registry();
        RegistryKey key = RegistryKey.of(RuneBuiltinRegistry.XSD_NS, "string");
        RosettaType userType = type("UserString");

        registry.register(key, userType);

        assertSame(userType, registry.resolve(key));
    }

    @Test
    void rejectsConflictingXmlConfigurationForOneRuneSymbol() {
        RuneTypeRegistry registry = registry();
        ModelSymbolId id = ModelSymbolId.fromQualifiedName("com.example.Foo");
        TypeXMLConfiguration first = xmlConfiguration("first");
        TypeXMLConfiguration second = xmlConfiguration("second");

        registry.registerConfig(id, first);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> registry.registerConfig(id, second));

        assertTrue(failure.getMessage().contains(id.toString()));
        assertEquals(first, registry.getXmlConfigs().get(id));
    }

    @Test
    void allowsExactIdempotentXmlConfigurationRegistration() {
        RuneTypeRegistry registry = registry();
        ModelSymbolId id = ModelSymbolId.fromQualifiedName("com.example.Foo");
        TypeXMLConfiguration configuration = xmlConfiguration("same");

        registry.registerConfig(id, configuration);
        registry.registerConfig(id, configuration);

        assertEquals(configuration, registry.getXmlConfigs().get(id));
    }

    @Test
    void mergesTypeOnlyAndExplicitElementConfigurationForOneRuneSymbol() {
        RuneTypeRegistry registry = registry();
        ModelSymbolId id = ModelSymbolId.fromQualifiedName("com.example.Foo");
        TypeXMLConfiguration typeConfiguration = new TypeXMLConfiguration(
                Optional.empty(),
                Optional.empty(),
                Optional.of("http://example.com/Foo"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        TypeXMLConfiguration elementConfiguration = new TypeXMLConfiguration(
                Optional.empty(),
                Optional.of("foo"),
                Optional.of("http://example.com/foo"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        registry.registerConfig(id, typeConfiguration);
        registry.registerConfig(id, elementConfiguration);

        assertEquals(Optional.of("foo"), registry.getXmlConfigs().get(id).getXmlElementName());
        assertEquals(Optional.of("http://example.com/foo"),
                registry.getXmlConfigs().get(id).getXmlElementFullyQualifiedName());
    }

    private RuneTypeRegistry registry() {
        return new RuneTypeRegistry(RegistryTestUtils.completeBuiltinRegistry());
    }

    private RosettaType type(String name) {
        RosettaType type = RosettaFactory.eINSTANCE.createRosettaTypeAlias();
        type.setName(name);
        return type;
    }

    private TypeXMLConfiguration xmlConfiguration(String xmlName) {
        return new TypeXMLConfiguration(
                Optional.empty(),
                Optional.empty(),
                Optional.of(xmlName),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
