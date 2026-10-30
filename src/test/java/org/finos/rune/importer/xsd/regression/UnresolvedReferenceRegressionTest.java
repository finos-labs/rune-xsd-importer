package org.finos.rune.importer.xsd.regression;

import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.integration.AbstractRuneIntegrationTest;
import org.finos.rune.importer.xsd.mapper.resolution.MappingResolutionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression coverage for parser-unresolved references that were previously dropped during
 * inspection, allowing generation to emit incomplete Rune models.
 */
@Tag("regression")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnresolvedReferenceRegressionTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get(
            "src/test/resources/org/finos/rune/xsd/regression/unresolvedreferences/unresolved-references.xsd");
    private static final String TARGET_NS = "urn:example:unresolved-references";

    @Test
    void rejectsUnresolvedGroupElementAttributeAndAttributeGroupReferencesBeforeMapping() {
        MappingResolutionException failure = assertThrows(MappingResolutionException.class,
                () -> generateRuneModels(XSD_FILE, temporaryDirectory.resolve("generated"), generationConfig()));

        String message = failure.getMessage();
        assertTrue(message.contains("MissingGroup"));
        assertTrue(message.contains("MissingElement"));
        assertTrue(message.contains("missingAttribute"));
        assertTrue(message.contains("MissingAttributeGroup"));
        assertTrue(message.contains("bad:MissingAttributeGroup"));
    }

    @Override
    protected Path generateScenarioModels() throws IOException {
        throw new UnsupportedOperationException("This regression asserts preflight rejection directly");
    }

    @Override
    protected void assertGeneratedModelContract(Path generatedOutput) {
        // This regression expects generation to fail before model shells are registered.
    }

    private RuneGeneratorConfig generationConfig() {
        return new RuneGeneratorConfig()
                .addTargetNamespace(TARGET_NS)
                .addNamespaceMapping(TARGET_NS, "com.example.unresolvedreferences");
    }

}
