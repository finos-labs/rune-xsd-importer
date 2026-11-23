package org.finos.rune.importer.xsd.regression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rosetta.model.lib.RosettaModelObject;
import org.finos.rune.importer.xsd.XsdValidationUtils;
import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.integration.AbstractRuneIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.xml.validation.Schema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("regression")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupRepeatedMemberRegressionTest extends AbstractRuneIntegrationTest {

    private static final Path RESOURCE_ROOT = Path.of(
            "src/test/resources/org/finos/rune/xsd/regression/grouprepeatedmember");
    private static final Path XSD_FILE = RESOURCE_ROOT.resolve("group-repeated-member.xsd");
    private static final String NAMESPACE = "http://example.com/group-repeated-member";
    private static final String RUNE_PACKAGE = "com.example.grouprepeatedmember";

    @ParameterizedTest(name = "round trips {0} entries")
    @MethodSource("entryCases")
    void roundTripsRepeatedMembersOfVirtualGroup(String entryCount, List<String> expectedEntries) throws Exception {
        Path input = RESOURCE_ROOT.resolve("group-repeated-member." + entryCount + "-entries.valid.xml");
        Schema schema = getSourceSchema(XSD_FILE);
        XsdValidationUtils.validate(schema, input);

        ScenarioState state = getScenarioState();
        Class<? extends RosettaModelObject> documentClass = state.compiledModel().getTypeJavaClass("Document");
        assertNotNull(documentClass, "Expected generated Document model");

        ObjectMapper mapper = getXmlMapper(
                state.generatedOutput().resolve("config/xml-config.json"), documentClass.getClassLoader());
        String originalXml = Files.readString(input);
        RosettaModelObject document = mapper.readValue(originalXml, documentClass);
        Object group = documentClass.getMethod("getEntriesModel").invoke(document);
        Class<?> groupClass = state.compiledModel().getTypeJavaClass("EntriesModel");
        assertNotNull(group);
        assertNotNull(groupClass);
        Object entries = groupClass.getMethod("getEntry").invoke(group);
        assertEquals(expectedEntries, entries == null ? List.of() : entries);
        String roundTripXml = mapper.writeValueAsString(document);

        Path output = state.generatedOutput().resolve("xml").resolve(input.getFileName());
        Files.createDirectories(output.getParent());
        Files.writeString(output, roundTripXml);
        XsdValidationUtils.validate(schema, output);
        assertXmlEquals(originalXml, roundTripXml);
    }

    private static Stream<Arguments> entryCases() {
        return Stream.of(
                Arguments.of("zero", List.of()),
                Arguments.of("one", List.of("first")),
                Arguments.of("two", List.of("first", "second")));
    }

    @Override
    protected Path generateScenarioModels() throws IOException {
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addTargetNamespace(NAMESPACE)
                .addNamespaceMapping(NAMESPACE, RUNE_PACKAGE);
        return generateRuneModels(XSD_FILE, temporaryDirectory.resolve("generated"), config);
    }

    @Override
    protected void assertGeneratedModelContract(Path generatedOutput) throws IOException {
        Path xmlConfig = generatedOutput.resolve("config/xml-config.json");
        JsonNode configuration = new ObjectMapper().readTree(xmlConfig.toFile());
        assertEquals("VIRTUAL", configuration.path(RUNE_PACKAGE + ".Document")
                .path("attributes").path("entriesModel").path("xmlRepresentation").asText());
    }
}
