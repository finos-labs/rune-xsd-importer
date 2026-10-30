package org.finos.rune.importer.xsd.core;

import com.regnosys.rosetta.rosetta.*;
import com.regnosys.rosetta.rosetta.simple.Attribute;
import com.regnosys.rosetta.rosetta.simple.Data;
import com.regnosys.rosetta.rosetta.simple.SimpleFactory;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuneImportPostProcessorTest {

    private static final RosettaFactory RUNE_FACTORY = RosettaFactory.eINSTANCE;
    private static final SimpleFactory SIMPLE_FACTORY = SimpleFactory.eINSTANCE;

    private final RuneImportPostProcessor processor = new RuneImportPostProcessor();

    @Test
    void processingTheSameModelTwiceDoesNotDuplicateImports() {
        RosettaModel source = model("com.example.source");
        addData(source, dataWithAttribute("Record", type("com.example.target", "Target")));

        processor.process(source);
        processor.process(source);

        assertEquals(List.of("com.example.target.*"), imports(source));
    }

    @Test
    void recognizesExistingWildcardImportsAndPreservesTheirSpelling() {
        RosettaModel source = model("com.example.source");
        source.getImports().add(importedNamespace(" com.example.target.* "));
        addData(source, dataWithAttribute("Record", type("com.example.target", "Target")));

        processor.process(source);

        assertEquals(List.of(" com.example.target.* "), imports(source));
    }

    @Test
    void treatsBareAndWildcardImportsAsEquivalentWithoutChangingExistingImports() {
        RosettaModel source = model("com.example.source");
        source.getImports().add(importedNamespace("com.example.target"));
        source.getImports().add(importedNamespace("com.example.unrelated.*"));
        addData(source, dataWithAttribute("Record", type("com.example.target", "Target")));

        processor.process(source);

        assertEquals(
                List.of("com.example.target", "com.example.unrelated.*"),
                imports(source));
    }

    @Test
    void collectsCrossNamespaceDataSuperTypeReferences() {
        RosettaModel source = model("com.example.source");
        Data child = data("Child");
        child.setSuperType(dataType("com.example.base", "Base"));
        addData(source, child);

        processor.process(source);

        assertEquals(List.of("com.example.base.*"), imports(source));
    }

    @Test
    void collectsCrossNamespaceDataAttributeReferences() {
        RosettaModel source = model("com.example.source");
        addData(source, dataWithAttribute("Record", type("com.example.target", "Target")));

        processor.process(source);

        assertEquals(List.of("com.example.target.*"), imports(source));
    }

    @Test
    void collectsCrossNamespaceAliasTypeCallReferences() {
        RosettaModel source = model("com.example.source");
        RosettaTypeAlias alias = alias("LocalAlias", source);
        alias.setTypeCall(typeCall(type("com.example.target", "Target")));
        source.getElements().add(alias);

        processor.process(source);

        assertEquals(List.of("com.example.target.*"), imports(source));
    }

    @Test
    void collectsCrossNamespaceAliasTypeParameterReferences() {
        RosettaModel source = model("com.example.source");
        RosettaTypeAlias alias = alias("LocalAlias", source);
        TypeParameter parameter = RUNE_FACTORY.createTypeParameter();
        parameter.setName("parameter");
        parameter.setTypeCall(typeCall(type("com.example.target", "Target")));
        alias.getParameters().add(parameter);
        source.getElements().add(alias);

        processor.process(source);

        assertEquals(List.of("com.example.target.*"), imports(source));
    }

    @Test
    void doesNotImportSamePackageReferences() {
        RosettaModel source = model("com.example.source");
        addData(source, dataWithAttribute("Record", type("com.example.source", "Target")));

        processor.process(source);

        assertTrue(source.getImports().isEmpty());
    }

    @Test
    void excludesOnlyTheExactStandardLibraryNamespace() {
        RosettaModel source = model("com.example.source");
        Data data = data("Record");
        data.getAttributes().add(attribute("builtin", type("com.rosetta.model", "string")));
        data.getAttributes().add(attribute("userType", type("com.rosetta.modelx", "UserType")));
        addData(source, data);

        processor.process(source);

        assertEquals(List.of("com.rosetta.modelx.*"), imports(source));
    }

    @Test
    void emitsNewImportsInSortedOrderAfterExistingImports() {
        RosettaModel source = model("com.example.source");
        source.getImports().add(importedNamespace("com.example.existing.*"));
        Data data = data("Record");
        data.getAttributes().add(attribute("z", type("com.example.zulu", "Zulu")));
        data.getAttributes().add(attribute("a", type("com.example.alpha", "Alpha")));
        addData(source, data);

        processor.process(source);

        assertEquals(
                List.of("com.example.existing.*", "com.example.alpha.*", "com.example.zulu.*"),
                imports(source));
    }

    @Test
    void skipsNullAndIncompleteModelStateWithoutCreatingMisleadingImports() {
        assertDoesNotThrow(() -> processor.process(null));

        RosettaModel unnamed = model(" ");
        Data data = data("Record");
        data.getAttributes().add(attribute("missingModel", type(null, "MissingModel")));
        data.getAttributes().add(attribute("missingType", null));
        addData(unnamed, data);

        assertDoesNotThrow(() -> processor.process(unnamed));
        assertTrue(unnamed.getImports().isEmpty());
    }

    @Test
    void malformedOrBlankExistingImportsDoNotMaskRequiredImports() {
        RosettaModel source = model("com.example.source");
        source.getImports().add(importedNamespace(null));
        source.getImports().add(importedNamespace("   "));
        addData(source, dataWithAttribute("Record", type("com.example.target", "Target")));

        processor.process(source);

        assertEquals(
                Arrays.asList(null, "   ", "com.example.target.*"),
                imports(source));
    }

    private static RosettaModel model(String name) {
        RosettaModel model = RUNE_FACTORY.createRosettaModel();
        model.setName(name);
        return model;
    }

    private static Data data(String name) {
        Data data = SIMPLE_FACTORY.createData();
        data.setName(name);
        return data;
    }

    private static Data dataType(String modelName, String name) {
        Data data = data(name);
        data.setModel(model(modelName));
        return data;
    }

    private static Data dataWithAttribute(String name, RosettaType type) {
        Data data = data(name);
        data.getAttributes().add(attribute("value", type));
        return data;
    }

    private static Attribute attribute(String name, RosettaType type) {
        Attribute attribute = SIMPLE_FACTORY.createAttribute();
        attribute.setName(name);
        if (type != null) {
            attribute.setTypeCall(typeCall(type));
        }
        return attribute;
    }

    private static RosettaType type(String modelName, String name) {
        RosettaType type = RUNE_FACTORY.createRosettaTypeAlias();
        type.setName(name);
        if (modelName != null) {
            type.setModel(model(modelName));
        }
        return type;
    }

    private static RosettaTypeAlias alias(String name, RosettaModel model) {
        RosettaTypeAlias alias = RUNE_FACTORY.createRosettaTypeAlias();
        alias.setName(name);
        alias.setModel(model);
        return alias;
    }

    private static TypeCall typeCall(RosettaType type) {
        TypeCall call = RUNE_FACTORY.createTypeCall();
        call.setType(type);
        return call;
    }

    private static void addData(RosettaModel model, Data data) {
        data.setModel(model);
        model.getElements().add(data);
    }

    private static Import importedNamespace(String namespace) {
        Import imported = RUNE_FACTORY.createImport();
        imported.setImportedNamespace(namespace);
        return imported;
    }

    private static List<String> imports(RosettaModel model) {
        return model.getImports().stream().map(Import::getImportedNamespace).toList();
    }
}
