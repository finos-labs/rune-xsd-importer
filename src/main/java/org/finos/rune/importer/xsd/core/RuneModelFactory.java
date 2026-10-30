package org.finos.rune.importer.xsd.core;

import com.regnosys.rosetta.rosetta.RosettaFactory;
import com.regnosys.rosetta.rosetta.RosettaModel;
import jakarta.inject.Inject;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.finos.rune.importer.xsd.api.XsdImporterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Infrastructure component responsible for managing the lifecycle of Rune Models.
 */
public class RuneModelFactory {

    public static final String PROJECT_VERSION = "${project.version}";

    private static final Logger log = LoggerFactory.getLogger(RuneModelFactory.class);

    private final ResourceSet resourceSet;
    private final Set<Resource> builtinResources = new HashSet<>();
    private static final RosettaFactory factory = RosettaFactory.eINSTANCE;

    @Inject
    public RuneModelFactory(ResourceSet resourceSet) {
        this.resourceSet = resourceSet;

        for(Resource r : resourceSet.getResources()) {
            if (r.getURI() != null && !r.getContents().isEmpty()) {
                builtinResources.add(r);
            }
        }
    }

    /**
     * Creates a model resource using the selected importer configuration.
     */
    public RosettaModel createRosettaModel(String namespace, RuneModelCategory category, XsdImporterConfig importerConfig) {
        Resource resource = createResource(namespace, category, importerConfig);
        RosettaModel model = factory.createRosettaModel();
        model.setName(namespace);
        model.setDefinition(namespace.concat(" definition"));
        model.setVersion(PROJECT_VERSION);

        resource.getContents().add(model);
        return model;
    }

    public void saveResources(Path outputRoot, XsdImporterConfig importerConfig) throws IOException {
        String modelFileExtension = Objects.requireNonNull(importerConfig, "importerConfig")
                .getModelFileExtension()
                .substring(1);
        List<Resource> resourcesToSave = resourceSet.getResources().stream()
                .filter(r -> !builtinResources.contains(r))
                .filter(r -> r.getURI() != null && modelFileExtension.equals(r.getURI().fileExtension()))
                .filter(r -> !r.getContents().isEmpty())
                .toList();

        for (Resource resource : resourcesToSave) {
            String fileName = resource.getURI().lastSegment();
            Path absolutePath = outputRoot.resolve(fileName).toAbsolutePath();

            if (Files.notExists(absolutePath.getParent())) {
                Files.createDirectories(absolutePath.getParent());
            }
            resource.setURI(URI.createFileURI(absolutePath.toString()));
        }

        for (Resource resource : resourcesToSave) {
            try {
                resource.save(Map.of());
            } catch (Exception e) {
                log.error("Failed to save: {}", resource.getURI(), e);
                throw new IOException("Error saving " + resource.getURI(), e);
            }
        }
    }

    private Resource createResource(
            String namespace,
            RuneModelCategory category,
            XsdImporterConfig importerConfig) {
        String modelFileExtension = Objects.requireNonNull(importerConfig, "importerConfig")
                .getModelFileExtension();
        ensureResourceFactoryRegistered(modelFileExtension);
        String safeName = namespace.replace('.', '_');
        String fileName = safeName + "-" + category.toString() + modelFileExtension;
        URI uri = URI.createURI(fileName);
        return resourceSet.createResource(uri);
    }

    private void ensureResourceFactoryRegistered(String modelFileExtension) {
        String extension = modelFileExtension.substring(1);
        Map<String, Object> extensionToFactory =
                resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap();
        if (extensionToFactory.containsKey(extension)) {
            return;
        }

        Object registeredFactory = extensionToFactory.get("rosetta");
        if (registeredFactory == null) {
            registeredFactory = extensionToFactory.get("rune");
        }
        if (registeredFactory == null) {
            registeredFactory = resourceSet.getResourceFactoryRegistry()
                    .getFactory(URI.createURI("model.rosetta"));
        }
        if (registeredFactory != null) {
            extensionToFactory.put(extension, registeredFactory);
        }
    }
}
