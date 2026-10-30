package org.finos.rune.importer.xsd.core;

import com.regnosys.rosetta.rosetta.RosettaModel;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.finos.rune.importer.xsd.api.XsdImporterConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RuneModelFactoryTest {

    @Test
    void snapshotsBuiltInsAtConstructionAndEvaluatesLaterResourcesByCurrentFilters(@TempDir Path outputRoot)
            throws IOException {
        ResourceSet resourceSet = newResourceSet();
        MarkerResource builtIn = addMarkerResource(resourceSet, "built-in.rune", "built-in");
        MarkerResource initiallyEmpty = addMarkerResource(resourceSet, "initially-empty.rune", "initially-empty");
        initiallyEmpty.getContents().clear();

        RuneModelFactory factory = newFactory(resourceSet);
        MarkerResource addedLater = addMarkerResource(resourceSet, "added-later.rune", "added-later");
        initiallyEmpty.getContents().add(EcoreFactory.eINSTANCE.createEClass());

        saveDefaultResources(factory, outputRoot);

        assertFalse(Files.exists(outputRoot.resolve("built-in.rune")));
        assertTrue(Files.exists(outputRoot.resolve("initially-empty.rune")));
        assertTrue(Files.exists(outputRoot.resolve("added-later.rune")));
        assertSame(builtIn, resourceSet.getResources().getFirst());
        assertTrue(addedLater.getURI().isFile());
    }

    @Test
    void preservesBuiltInSnapshotMembershipWhenResourcesAreReorderedAndMutated(@TempDir Path outputRoot)
            throws IOException {
        ResourceSet resourceSet = newResourceSet();
        MarkerResource builtIn = addMarkerResource(resourceSet, "built-in.rune", "built-in");
        RuneModelFactory factory = newFactory(resourceSet);
        MarkerResource later = addMarkerResource(resourceSet, "later.rune", "later");

        resourceSet.getResources().remove(builtIn);
        resourceSet.getResources().add(builtIn);
        builtIn.setMarker("mutated-built-in");

        saveDefaultResources(factory, outputRoot);

        assertFalse(Files.exists(outputRoot.resolve("built-in.rune")));
        assertTrue(Files.exists(outputRoot.resolve("later.rune")));
        assertTrue(later.getURI().isFile());
        assertNotEquals(URI.createFileURI(outputRoot.resolve("built-in.rune").toAbsolutePath().toString()),
                builtIn.getURI());
    }

    @Test
    void createsSanitizedUrisAndKeepsConfigAndVersionBehavior(@TempDir Path outputRoot) throws IOException {
        ResourceSet resourceSet = newResourceSet();
        RuneModelFactory factory = newFactory(resourceSet);

        RosettaModel first = createDefaultModel(factory, "a.b", RuneModelCategory.TYPE);
        RosettaModel second = createDefaultModel(factory, "a_b", RuneModelCategory.TYPE);
        RosettaModel repeated = createDefaultModel(factory, "a.b", RuneModelCategory.TYPE);
        RosettaModel differentCategory = createDefaultModel(factory, "a.b", RuneModelCategory.ENUM);

        assertEquals("a_b-type.rune", first.eResource().getURI().toString());
        assertEquals("a_b-type.rune", second.eResource().getURI().toString());
        assertEquals("a_b-type.rune", repeated.eResource().getURI().toString());
        assertEquals("a_b-enum.rune", differentCategory.eResource().getURI().toString());
        assertEquals(4, resourceSet.getResources().size());
        assertEquals(RuneModelFactory.PROJECT_VERSION, first.getVersion());
        assertEquals("a.b", first.getName());
        assertEquals("a.b definition", first.getDefinition());

        saveDefaultResources(factory, outputRoot);
        assertTrue(Files.exists(outputRoot.resolve("a_b-type.rune")));
        assertTrue(Files.exists(outputRoot.resolve("a_b-enum.rune")));
    }

    @Test
    void createsAndSavesConfiguredRosettaResourcesWithoutPublishingRuneResources(@TempDir Path outputRoot)
            throws IOException {
        ResourceSet resourceSet = newResourceSet();
        RuneModelFactory factory = newFactory(resourceSet);
        XsdImporterConfig rosettaConfig = XsdImporterConfig.builder()
                .modelFileExtension(XsdImporterConfig.ROSETTA_MODEL_FILE_EXTENSION)
                .build();

        factory.createRosettaModel("a.b", RuneModelCategory.TYPE, rosettaConfig);
        addMarkerResource(resourceSet, "wrong-type.rune", "wrong");

        factory.saveResources(outputRoot, rosettaConfig);

        assertTrue(Files.exists(outputRoot.resolve("a_b-type.rosetta")));
        assertFalse(Files.exists(outputRoot.resolve("wrong-type.rune")));
        assertTrue(resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().containsKey("rosetta"));
    }

    @Test
    void publishesDistinctInMemoryResourcesWhenExistingUrisCollideAndLastSaveWins(@TempDir Path outputRoot)
            throws IOException {
        ResourceSet resourceSet = newResourceSet();
        RuneModelFactory factory = newFactory(resourceSet);
        List<String> saveOrder = new ArrayList<>();
        MarkerResource first = addMarkerResource(resourceSet, "a_b-type.rune", "first", saveOrder);
        MarkerResource second = addMarkerResource(resourceSet, "a_b-type.rune", "second", saveOrder);

        saveDefaultResources(factory, outputRoot);

        assertEquals(List.of("first", "second"), saveOrder);
        assertEquals(2, resourceSet.getResources().size());
        assertEquals("second", Files.readString(outputRoot.resolve("a_b-type.rune")));
        assertTrue(first.getURI().isFile());
        assertTrue(second.getURI().isFile());
        assertEquals(first.getURI(), second.getURI());
    }

    @Test
    void savesEligibleResourcesAndMutatesTheirUrisBeforePersistence(@TempDir Path outputRoot) throws IOException {
        ResourceSet resourceSet = newResourceSet();
        RuneModelFactory factory = newFactory(resourceSet);
        MarkerResource resource = addMarkerResource(resourceSet, "logical.rune", "payload");
        URI logicalUri = resource.getURI();

        saveDefaultResources(factory, outputRoot);

        assertNotEquals(logicalUri, resource.getURI());
        assertEquals(URI.createFileURI(outputRoot.resolve("logical.rune").toAbsolutePath().toString()),
                resource.getURI());
        assertTrue(Files.exists(outputRoot.resolve("logical.rune")));
        assertEquals("payload", Files.readString(outputRoot.resolve("logical.rune")));
    }

    @Test
    void mutatesAllUrisBeforeReportingAFailureAndLeavesPartialOutput(@TempDir Path temporaryDirectory) {
        ResourceSet resourceSet = newResourceSet();
        RuneModelFactory factory = newFactory(resourceSet);
        List<String> saveOrder = new ArrayList<>();
        MarkerResource first = addMarkerResource(resourceSet, "first.rune", "first", saveOrder);
        MarkerResource failing = addMarkerResource(resourceSet, "failing.rune", "failing", saveOrder);
        failing.setFailOnSave(true);

        IOException failure = assertThrows(IOException.class, () -> saveDefaultResources(factory, temporaryDirectory));

        assertEquals("Error saving " + failing.getURI(), failure.getMessage());
        assertInstanceOf(IOException.class, failure.getCause());
        assertEquals(List.of("first"), saveOrder);
        assertTrue(Files.exists(temporaryDirectory.resolve("first.rune")));
        assertFalse(Files.exists(temporaryDirectory.resolve("failing.rune")));
        assertTrue(first.getURI().isFile());
        assertTrue(failing.getURI().isFile());
    }

    @Test
    void wrapsAFilesystemFailureAfterMutatingResourceUris(@TempDir Path temporaryDirectory) throws IOException {
        ResourceSet resourceSet = newResourceSet();
        RuneModelFactory factory = newFactory(resourceSet);
        MarkerResource resource = addMarkerResource(resourceSet, "blocked.rune", "blocked");
        Path blockingFile = temporaryDirectory.resolve("blocking-file");
        Files.writeString(blockingFile, "not a directory");

        IOException failure = assertThrows(IOException.class, () -> saveDefaultResources(factory, blockingFile));

        assertTrue(failure.getMessage().startsWith("Error saving "));
        assertInstanceOf(IOException.class, failure.getCause());
        assertEquals(URI.createFileURI(blockingFile.resolve("blocked.rune").toAbsolutePath().toString()),
                resource.getURI());
    }

    @Test
    void retryUsesTheMutatedResourceAndCanWriteToACorrectedOutput(@TempDir Path temporaryDirectory) throws IOException {
        ResourceSet resourceSet = newResourceSet();
        RuneModelFactory factory = newFactory(resourceSet);
        MarkerResource resource = addMarkerResource(resourceSet, "retry.rune", "retry");
        Path blockingFile = temporaryDirectory.resolve("blocking-file");
        Path correctedOutput = temporaryDirectory.resolve("corrected");
        Files.writeString(blockingFile, "not a directory");
        URI originalUri = resource.getURI();

        assertThrows(IOException.class, () -> saveDefaultResources(factory, blockingFile));
        saveDefaultResources(factory, correctedOutput);

        assertNotEquals(originalUri, resource.getURI());
        assertEquals(URI.createFileURI(correctedOutput.resolve("retry.rune").toAbsolutePath().toString()),
                resource.getURI());
        assertTrue(Files.exists(correctedOutput.resolve("retry.rune")));
    }

    @Test
    void repeatedSavesRewriteTheSameOutputAndKeepTheFileUriStable(@TempDir Path outputRoot) throws IOException {
        ResourceSet resourceSet = newResourceSet();
        RuneModelFactory factory = newFactory(resourceSet);
        MarkerResource resource = addMarkerResource(resourceSet, "repeat.rune", "first");

        saveDefaultResources(factory, outputRoot);
        URI savedUri = resource.getURI();
        resource.setMarker("second");
        saveDefaultResources(factory, outputRoot);

        assertEquals(savedUri, resource.getURI());
        assertEquals("second", Files.readString(outputRoot.resolve("repeat.rune")));
    }

    @Test
    void exposesTheOriginalMutableResourceSetAndPublishesExternalEligibleResources(@TempDir Path outputRoot)
            throws IOException {
        ResourceSet resourceSet = newResourceSet();
        RuneModelFactory factory = newFactory(resourceSet);
        MarkerResource generated = addMarkerResource(resourceSet, "generated.rune", "generated");
        MarkerResource external = addMarkerResource(resourceSet, "unrelated.rune", "external");

        resourceSet.getResources().remove(generated);
        external.setMarker("changed externally");
        saveDefaultResources(factory, outputRoot);

        assertFalse(Files.exists(outputRoot.resolve("generated.rune")));
        assertEquals("changed externally", Files.readString(outputRoot.resolve("unrelated.rune")));
        assertTrue(external.getURI().isFile());
    }

    @Test
    void followsCurrentResourceSetOrderAndMakesTheLastDuplicateWriterWin(@TempDir Path outputRoot)
            throws IOException {
        ResourceSet resourceSet = newResourceSet();
        RuneModelFactory factory = newFactory(resourceSet);
        List<String> saveOrder = new ArrayList<>();
        MarkerResource first = addMarkerResource(resourceSet, "same.rune", "first", saveOrder);
        addMarkerResource(resourceSet, "same.rune", "second", saveOrder);

        saveDefaultResources(factory, outputRoot);
        assertEquals(List.of("first", "second"), saveOrder);
        assertEquals("second", Files.readString(outputRoot.resolve("same.rune")));

        saveOrder.clear();
        resourceSet.getResources().remove(first);
        resourceSet.getResources().addFirst(first);
        saveDefaultResources(factory, outputRoot);

        assertEquals(List.of("first", "second"), saveOrder);
        assertEquals("second", Files.readString(outputRoot.resolve("same.rune")));
    }

    @Test
    void ignoresEmptyNullAndNonRuneResources(@TempDir Path outputRoot) throws IOException {
        ResourceSet resourceSet = newResourceSet();
        RuneModelFactory factory = newFactory(resourceSet);
        MarkerResource empty = addMarkerResource(resourceSet, "empty.rune", "empty");
        empty.getContents().clear();
        MarkerResource noExtension = addMarkerResource(resourceSet, "not-rune.txt", "text");
        MarkerResource nullUri = addMarkerResource(resourceSet, null, "null-uri");
        MarkerResource blankUri = addMarkerResource(resourceSet, "", "blank-uri");

        saveDefaultResources(factory, outputRoot);

        assertFalse(Files.exists(outputRoot.resolve("empty.rune")));
        assertFalse(Files.exists(outputRoot.resolve("not-rune.txt")));
        assertFalse(Files.exists(outputRoot.resolve("null-uri")));
        assertFalse(Files.exists(outputRoot.resolve("blank-uri")));
        assertEquals(URI.createURI("not-rune.txt"), noExtension.getURI());
        assertEquals(URI.createURI(""), blankUri.getURI());
        assertNull(nullUri.getURI());
    }

    @Test
    void preservesTheCurrentExceptionBaselineForNullAndMalformedInputs(@TempDir Path outputRoot) {

        ResourceSet resourceSet = newResourceSet();
        RuneModelFactory factory = newFactory(resourceSet);
        assertThrows(NullPointerException.class,
                () -> createDefaultModel(factory, null, RuneModelCategory.TYPE));
        assertThrows(NullPointerException.class,
                () -> createDefaultModel(factory, "namespace", null));
        assertDoesNotThrow(() -> createDefaultModel(factory, "", RuneModelCategory.TYPE));
        assertThrows(NullPointerException.class,
                () -> createDefaultModel(factory, "a/b?c", RuneModelCategory.TYPE));
        assertThrows(NullPointerException.class, () -> saveDefaultResources(factory, null));

        MarkerResource outputFileResource = addMarkerResource(resourceSet, "output-file.rune", "output-file");
        Path outputFile = outputRoot.resolve("output-file");
        assertDoesNotThrow(() -> Files.writeString(outputFile, "blocking"));
        IOException failure = assertThrows(IOException.class, () -> saveDefaultResources(factory, outputFile));
        assertTrue(failure.getMessage().startsWith("Error saving "));
        assertTrue(outputFileResource.getURI().isFile());
    }

    private RuneModelFactory newFactory(ResourceSet resourceSet) {
        return new RuneModelFactory(resourceSet);
    }

    private RosettaModel createDefaultModel(
            RuneModelFactory factory,
            String namespace,
            RuneModelCategory category) {
        return factory.createRosettaModel(namespace, category, XsdImporterConfig.defaults());
    }

    private void saveDefaultResources(RuneModelFactory factory, Path outputRoot) throws IOException {
        factory.saveResources(outputRoot, XsdImporterConfig.defaults());
    }

    private ResourceSet newResourceSet() {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("rune", new XMIResourceFactoryImpl());
        return resourceSet;
    }

    private MarkerResource addMarkerResource(ResourceSet resourceSet, String uri, String marker) {
        return addMarkerResource(resourceSet, uri, marker, new ArrayList<>());
    }

    private MarkerResource addMarkerResource(
            ResourceSet resourceSet, String uri, String marker, List<String> saveOrder) {
        MarkerResource resource = new MarkerResource(uri == null ? null : URI.createURI(uri), marker, saveOrder);
        resourceSet.getResources().add(resource);
        resource.getContents().add(EcoreFactory.eINSTANCE.createEClass());
        return resource;
    }

    private static final class MarkerResource extends org.eclipse.emf.ecore.resource.impl.ResourceImpl {

        private String marker;
        private final List<String> saveOrder;
        private boolean failOnSave;

        private MarkerResource(URI uri, String marker, List<String> saveOrder) {
            super(uri);
            this.marker = marker;
            this.saveOrder = saveOrder;
        }

        @Override
        public void save(Map<?, ?> options) throws IOException {
            if (failOnSave) {
                throw new IOException("controlled save failure: " + marker);
            }
            saveOrder.add(marker);
            Files.writeString(Path.of(getURI().toFileString()), marker, StandardCharsets.UTF_8);
        }

        private void setMarker(String marker) {
            this.marker = marker;
        }

        private void setFailOnSave(boolean failOnSave) {
            this.failOnSave = failOnSave;
        }
    }
}
