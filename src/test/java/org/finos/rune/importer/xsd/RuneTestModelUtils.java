package org.finos.rune.importer.xsd;

import org.finos.rune.importer.xsd.api.XsdImporterConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Utilities for deterministic loading of generated Rune sources in integration tests.
 */
public final class RuneTestModelUtils {

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("(?m)^\\s*namespace\\s+([^\\s:]+)\\s*:");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*)(?:\\.\\*)?\\s*$");

    private RuneTestModelUtils() {
    }

    public static List<String> readRuneSources(Path generatedOutputDirectory) throws IOException {
        return readRuneSources(generatedOutputDirectory, null);
    }

    /**
     * Reads generated Rune sources with an optional primary namespace and deterministic import ordering.
     *
     * <p>The primary namespace is placed first because the dynamic test-model
     * compiler exposes the target type from its primary source. Remaining
     * namespaces are ordered by their Rune import dependencies, with type files
     * before enum files as a deterministic tie-breaker.</p>
     */
    public static List<String> readRuneSources(Path generatedOutputDirectory, String primaryNamespace) throws IOException {
        return readRuneSources(
                generatedOutputDirectory,
                primaryNamespace,
                XsdImporterConfig.DEFAULT_MODEL_FILE_EXTENSION);
    }

    /**
     * Reads generated model sources using the configured model-file extension.
     */
    public static List<String> readRuneSources(
            Path generatedOutputDirectory,
            String primaryNamespace,
            String modelFileExtension) throws IOException {
        String normalizedExtension = XsdImporterConfig.normalizeModelFileExtension(modelFileExtension);
        List<RuneSource> sources;
        try (var paths = Files.walk(generatedOutputDirectory)) {
            sources = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(normalizedExtension))
                    .map(RuneTestModelUtils::readRuneSourceMetadata)
                    .toList();
        }

        if (sources.isEmpty()) {
            return List.of();
        }

        Comparator<RuneSource> deterministicOrder = Comparator
                .comparingInt((RuneSource source) -> runeFileRank(source.path(), normalizedExtension))
                .thenComparing(source -> source.path().getFileName().toString());

        Map<String, List<RuneSource>> sourcesByNamespace = new HashMap<>();
        for (RuneSource source : sources) {
            sourcesByNamespace.computeIfAbsent(source.namespace(), ignored -> new ArrayList<>()).add(source);
        }
        sourcesByNamespace.values().forEach(namespaceSources -> namespaceSources.sort(deterministicOrder));

        List<RuneSource> orderedSources = new ArrayList<>();
        Set<RuneSource> remainingSources = new HashSet<>(sources);
        if (primaryNamespace != null) {
            List<RuneSource> primarySources = sourcesByNamespace.get(primaryNamespace);
            if (primarySources == null) {
                throw new IllegalArgumentException("No generated Rune source found for primary namespace: " + primaryNamespace);
            }
            orderedSources.addAll(primarySources);
            remainingSources.removeAll(primarySources);
        }

        orderedSources.addAll(orderByImports(remainingSources, sourcesByNamespace, deterministicOrder));
        return orderedSources.stream().map(RuneSource::content).toList();
    }

    private static int runeFileRank(Path path, String modelFileExtension) {
        String name = path.getFileName().toString();
        if (name.endsWith("-type" + modelFileExtension)) {
            return 1;
        }
        if (name.endsWith("-enum" + modelFileExtension)) {
            return 2;
        }
        return 3;
    }

    private static List<RuneSource> orderByImports(Set<RuneSource> sources,
                                                   Map<String, List<RuneSource>> sourcesByNamespace,
                                                   Comparator<RuneSource> deterministicOrder) {
        if (sources.isEmpty()) {
            return List.of();
        }

        Map<RuneSource, Integer> dependencyCount = new HashMap<>();
        Map<RuneSource, List<RuneSource>> dependants = new HashMap<>();
        for (RuneSource source : sources) {
            Set<RuneSource> dependencies = new HashSet<>();
            for (String importedNamespace : source.importedNamespaces()) {
                List<RuneSource> importedSources = sourcesByNamespace.get(importedNamespace);
                if (importedSources != null) {
                    dependencies.addAll(importedSources);
                }
            }
            dependencies.retainAll(sources);
            dependencyCount.put(source, dependencies.size());
            for (RuneSource dependency : dependencies) {
                dependants.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(source);
            }
        }

        List<RuneSource> ready = dependencyCount.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .sorted(deterministicOrder)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        ArrayDeque<RuneSource> readyQueue = new ArrayDeque<>(ready);
        List<RuneSource> orderedSources = new ArrayList<>();

        while (!readyQueue.isEmpty()) {
            RuneSource source = readyQueue.removeFirst();
            orderedSources.add(source);

            List<RuneSource> sourceDependants = dependants.getOrDefault(source, List.of());
            for (RuneSource dependant : sourceDependants) {
                int remainingDependencies = dependencyCount.merge(dependant, -1, Integer::sum);
                if (remainingDependencies == 0) {
                    readyQueue.add(dependant);
                }
            }
            List<RuneSource> reorderedReady = new ArrayList<>(readyQueue);
            reorderedReady.sort(deterministicOrder);
            readyQueue = new ArrayDeque<>(reorderedReady);
        }

        if (orderedSources.size() != sources.size()) {
            Set<RuneSource> unresolved = new HashSet<>(sources);
            unresolved.removeAll(orderedSources);
            String unresolvedFiles = unresolved.stream()
                    .sorted(deterministicOrder)
                    .map(source -> source.path().getFileName().toString())
                    .collect(java.util.stream.Collectors.joining(", "));
            throw new IllegalStateException("Unable to order generated Rune sources because of an import cycle involving: " + unresolvedFiles);
        }
        return orderedSources;
    }

    private static RuneSource readRuneSourceMetadata(Path path) {
        String content = readRuneSource(path);
        Matcher namespaceMatcher = NAMESPACE_PATTERN.matcher(content);
        if (!namespaceMatcher.find()) {
            throw new IllegalArgumentException("Generated Rune source has no namespace declaration: " + path);
        }

        Set<String> importedNamespaces = new HashSet<>();
        Matcher importMatcher = IMPORT_PATTERN.matcher(content);
        while (importMatcher.find()) {
            importedNamespaces.add(importMatcher.group(1));
        }
        return new RuneSource(path, content, namespaceMatcher.group(1), importedNamespaces);
    }

    private static String readRuneSource(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read generated Rune source " + path, exception);
        }
    }

    private record RuneSource(Path path, String content, String namespace, Set<String> importedNamespaces) {
    }

    public static void assertDeclarationContains(String source, String declarationHeader, String expectedText) {
        String declaration = findDeclaration(source, declarationHeader);
        assertTrue(declaration.contains(expectedText), "Missing '" + expectedText + "' in " + declarationHeader);
    }

    private static String findDeclaration(String source, String declarationHeader) {
        Pattern pattern = Pattern.compile("(?ms)^" + Pattern.quote(declarationHeader) + ".*?(?=^(?:type|typeAlias|enum)\\s+|\\z)");
        Matcher matcher = pattern.matcher(source);
        assertTrue(matcher.find(), "Missing generated declaration " + declarationHeader);
        return matcher.group();
    }
}
