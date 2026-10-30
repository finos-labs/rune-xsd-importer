package org.finos.rune.importer.xsd.api;

import com.google.inject.Injector;
import com.regnosys.rosetta.RosettaStandaloneSetup;
import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.resource.IResourceFactory;
import org.eclipse.xtext.resource.IResourceServiceProvider;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.finos.rune.importer.xsd.core.EmfRuneGenerator;
import org.finos.rune.importer.xsd.core.RuneModelFactory;
import org.finos.rune.importer.xsd.registry.RuneBuiltinRegistry;
import org.finos.rune.importer.xsd.registry.RuneTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class XsdImporterDefaultSetup {

    private static final Logger log = LoggerFactory.getLogger(XsdImporterDefaultSetup.class);

    private XsdImporterDefaultSetup() {}

    /**
     * Bootstraps the EMF Resource Sets, standard library built-ins,
     * and the dependency chain required to instantiate the EmfRuneGenerator.
     */
    public static EmfRuneGenerator setupInfrastructure() {
        log.trace("Bootstrapping Generation Infrastructure (Phase A).");

        RosettaStandaloneSetup setup = new RosettaStandaloneSetup();
        Injector injector = setup.createInjectorAndDoEMFRegistration();

        XtextResourceSet resourceSet = injector.getInstance(XtextResourceSet.class);
        IResourceFactory rosettaFactory = injector.getInstance(IResourceFactory.class);
        IResourceServiceProvider serviceProvider = injector.getInstance(IResourceServiceProvider.class);

        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("rune", rosettaFactory);
        IResourceServiceProvider.Registry.INSTANCE.getExtensionToFactoryMap().put("rune", serviceProvider);

        loadStandardLibrary(resourceSet);

        RuneModelFactory modelFactory = new RuneModelFactory(resourceSet);
        RuneBuiltinRegistry builtinRegistry = new RuneBuiltinRegistry(resourceSet);
        RuneTypeRegistry typeRegistry = new RuneTypeRegistry(builtinRegistry);

        EmfRuneGenerator generator = new EmfRuneGenerator(modelFactory, typeRegistry);
        log.debug("EmfRuneGenerator successfully created.");
        return generator;
    }

    /**
     * Loads base Rune semantic models into the in-memory EMF ResourceSet.
     * @param resourceSet The active EMF ResourceSet.
     */
    private static void loadStandardLibrary(XtextResourceSet resourceSet) {
        List<String> libraryFiles = List.of("model/basictypes.rosetta", "model/annotations.rosetta");
        for (String path : libraryFiles) {
            try {
                var url = XsdImporterDefaultSetup.class.getClassLoader().getResource(path);
                if (url != null) {
                    resourceSet.createResource(URI.createURI(url.toString())).load(null);
                } else {
                    log.warn("Standard library resource was not found: {}", path);
                }
            } catch (Exception e) {
                log.warn("Could not load standard library built-in: {}", path, e);
            }
        }
    }
}
