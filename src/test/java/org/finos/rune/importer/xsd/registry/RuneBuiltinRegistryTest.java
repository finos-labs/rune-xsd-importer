package org.finos.rune.importer.xsd.registry;

import com.regnosys.rosetta.rosetta.RosettaFactory;
import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.rosetta.RosettaType;
import com.regnosys.rosetta.rosetta.simple.Data;
import com.regnosys.rosetta.rosetta.simple.SimpleFactory;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuneBuiltinRegistryTest {

    @Test
    void nullResourceSetFailsDuringConstruction() {
        assertThrows(NullPointerException.class, () -> new RuneBuiltinRegistry(null));
    }

    @Test
    void rejectsAnEmptyResourceSetDuringConstruction() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new RuneBuiltinRegistry(new ResourceSetImpl()));

        assertTrue(failure.getMessage().contains("com.rosetta.model"));
        assertTrue(failure.getMessage().contains("not initialized"));
    }

    @Test
    void rejectsResourcesWithoutTheStandardLibraryDuringConstruction() {
        ResourceSet resourceSet = resourceSetWithModel("com.example.unrelated");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new RuneBuiltinRegistry(resourceSet));

        assertTrue(failure.getMessage().contains("com.rosetta.model"));
    }

    @Test
    void rejectsAnIncompleteStandardLibraryDuringConstruction() {
        Data stringType = SimpleFactory.eINSTANCE.createData();
        stringType.setName("string");
        ResourceSet resourceSet = resourceSetWithModel("com.rosetta.model", stringType);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new RuneBuiltinRegistry(resourceSet));

        assertTrue(failure.getMessage().contains("incomplete"));
        assertTrue(failure.getMessage().contains("int"));
    }

    @Test
    void indexesBuiltinMappingsWhenTheStandardLibraryIsPresent() {
        RuneBuiltinRegistry registry = RegistryTestUtils.completeBuiltinRegistry();

        assertNotNull(registry.get(RegistryKey.of(RuneBuiltinRegistry.XSD_NS, "string")));
    }

    @Test
    void classifiesBuiltinPrimitiveFamiliesByExactQualifiedName() {
        assertEquals(
                "date",
                RuneBuiltinRegistry.primitiveFamily(RegistryKey.of(RuneBuiltinRegistry.XSD_NS, "date"))
                        .orElseThrow());
        assertEquals(
                "dateTime",
                RuneBuiltinRegistry.primitiveFamily(RegistryKey.of(RuneBuiltinRegistry.XSD_NS, "dateTime"))
                        .orElseThrow());
        assertEquals(
                "int",
                RuneBuiltinRegistry.primitiveFamily(RegistryKey.of(RuneBuiltinRegistry.XSD_NS, "nonNegativeInteger"))
                        .orElseThrow());
        assertEquals(
                "number",
                RuneBuiltinRegistry.primitiveFamily(RegistryKey.of(RuneBuiltinRegistry.XSD_NS, "decimal"))
                        .orElseThrow());
        assertTrue(RuneBuiltinRegistry.primitiveFamily(
                RegistryKey.of("http://example.com/custom", "dateTime")).isEmpty());
    }

    private ResourceSet resourceSetWithModel(String name, RosettaType... types) {
        RosettaModel model = RosettaFactory.eINSTANCE.createRosettaModel();
        model.setName(name);
        model.getElements().addAll(List.of(types));

        ResourceSet resourceSet = new ResourceSetImpl();
        ResourceImpl resource = new ResourceImpl(URI.createURI("memory:/" + name + ".rosetta"));
        resource.getContents().add(model);
        resourceSet.getResources().add(resource);
        return resourceSet;
    }
}
