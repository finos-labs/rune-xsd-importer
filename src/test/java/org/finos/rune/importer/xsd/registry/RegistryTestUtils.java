package org.finos.rune.importer.xsd.registry;

import com.regnosys.rosetta.rosetta.RosettaFactory;
import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.rosetta.simple.Data;
import com.regnosys.rosetta.rosetta.simple.SimpleFactory;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;

import java.util.List;

public final class RegistryTestUtils {

    private static final List<String> BUILTIN_TYPE_NAMES = List.of(
            "string",
            "int",
            "number",
            "boolean",
            "date",
            "time",
            "zonedDateTime");

    private RegistryTestUtils() {
    }

    public static RuneBuiltinRegistry completeBuiltinRegistry() {
        return new RuneBuiltinRegistry(completeResourceSet());
    }

    public static ResourceSet completeResourceSet() {
        RosettaModel model = RosettaFactory.eINSTANCE.createRosettaModel();
        model.setName("com.rosetta.model");
        for (String name : BUILTIN_TYPE_NAMES) {
            Data type = SimpleFactory.eINSTANCE.createData();
            type.setName(name);
            model.getElements().add(type);
        }

        ResourceSet resourceSet = new ResourceSetImpl();
        ResourceImpl resource = new ResourceImpl(URI.createURI("memory:/com.rosetta.model.rosetta"));
        resource.getContents().add(model);
        resourceSet.getResources().add(resource);
        return resourceSet;
    }
}
