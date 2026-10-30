package org.finos.rune.importer.xsd.core;

import com.regnosys.rosetta.rosetta.*;
import com.regnosys.rosetta.rosetta.simple.Attribute;
import com.regnosys.rosetta.rosetta.simple.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Post-processor that scans a fully populated RosettaModel and generates
 * the necessary 'import' statements for cross-namespace references.
 * <p>
 * Processing mutates the caller-owned {@link RosettaModel} in place by adding
 * imports. Rosetta EMF models are not generally thread-safe, so callers must
 * provide single-threaded access to a model while it is being processed and
 * must not use the same model concurrently with other model mutations.
 */
public class RuneImportPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(RuneImportPostProcessor.class);
    private static final String BUILTIN_NAMESPACE = "com.rosetta.model";

    private static final RosettaFactory runeFactory = RosettaFactory.eINSTANCE;

    public void process(RosettaModel model) {
        if (model == null) {
            log.warn("Skipping Rune import processing for a null RosettaModel");
            return;
        }

        String currentPackage = model.getName();
        if (isBlank(currentPackage)) {
            log.warn("Skipping Rune import processing for a model with a null or blank name");
            return;
        }

        Set<String> dependencies = new TreeSet<>();

        for (var element : model.getElements()) {
            if (element instanceof Data data) {
                collectDataDependencies(data, currentPackage, dependencies);
            } else if (element instanceof RosettaTypeAlias alias) {
                collectAliasDependencies(alias, currentPackage, dependencies);
            }
        }

        Set<String> existingImports = model.getImports().stream()
                .filter(Objects::nonNull)
                .map(Import::getImportedNamespace)
                .map(this::normalizeNamespace)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (String dependency : dependencies) {
            if (existingImports.add(dependency)) {
                Import imp = runeFactory.createImport();
                imp.setImportedNamespace(dependency + ".*");
                model.getImports().add(imp);
            }
        }
    }

    private void collectDataDependencies(Data data, String currentPackage, Set<String> dependencies) {
        if (data.getSuperType() != null) {
            checkAndCollect(data.getSuperType(), currentPackage, dependencies);
        }

        for (Attribute attr : data.getAttributes()) {
            if (attr != null) {
                collectTypeCallDependencies(attr.getTypeCall(), currentPackage, dependencies);
            }
        }
    }

    private void collectAliasDependencies(
            RosettaTypeAlias alias,
            String currentPackage,
            Set<String> dependencies) {
        collectTypeCallDependencies(alias.getTypeCall(), currentPackage, dependencies);

        for (TypeParameter parameter : alias.getParameters()) {
            if (parameter != null) {
                collectTypeCallDependencies(parameter.getTypeCall(), currentPackage, dependencies);
            }
        }
    }

    private void collectTypeCallDependencies(
            TypeCall typeCall,
            String currentPackage,
            Set<String> dependencies) {
        if (typeCall != null) {
            checkAndCollect(typeCall.getType(), currentPackage, dependencies);
        }
    }

    private void checkAndCollect(RosettaType type, String currentPackage, Set<String> dependencies) {
        if (type == null) {
            return;
        }

        RosettaModel targetModel = type.getModel();
        if (targetModel == null) {
            log.warn("Skipping Rune import dependency for type '{}' without a containing model", type.getName());
            return;
        }

        String targetPackage = targetModel.getName();
        if (isBlank(targetPackage)) {
            log.warn("Skipping Rune import dependency for type '{}' whose containing model has a null or blank name", type.getName());
            return;
        }

        // Built-ins in the exact standard library namespace are already available implicitly.
        if (!targetPackage.equals(currentPackage) && !BUILTIN_NAMESPACE.equals(targetPackage)) {
            dependencies.add(targetPackage);
        }
    }

    private String normalizeNamespace(String importedNamespace) {
        if (isBlank(importedNamespace)) {
            return null;
        }

        String normalized = importedNamespace.trim();
        return normalized.endsWith(".*")
                ? normalized.substring(0, normalized.length() - 2)
                : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
