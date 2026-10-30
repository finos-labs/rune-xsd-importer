package org.finos.rune.importer.xsd.core;

import org.finos.rune.importer.xsd.inspector.*;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class XsdDependencyValidatorTest {

    private static final String SOURCE_NS = "http://example.com/source";
    private static final String IMPORTED_NS = "http://example.com/imported";

    @Test
    void acceptsForwardAndCrossNamespaceReferences() {
        RegistryKey child = RegistryKey.of(SOURCE_NS, "Child");
        RegistryKey base = RegistryKey.of(IMPORTED_NS, "Base");

        List<String> errors = XsdDependencyValidator.validateGraph(
                Set.of(type(child), type(base)),
                List.of(new XsdDependency(type(child), base.toString(), XsdDeclarationRole.TYPE, "Child base",
                        XsdDependencyType.BASE_TYPE_REFERENCE)),
                Set.of(SOURCE_NS));

        assertTrue(errors.isEmpty());
    }

    @Test
    void keepsEqualLocalNamesQualified() {
        RegistryKey source = RegistryKey.of(SOURCE_NS, "Child");
        RegistryKey imported = RegistryKey.of(IMPORTED_NS, "SharedType");
        RegistryKey unrelated = RegistryKey.of("http://example.com/unrelated", "SharedType");

        List<String> errors = XsdDependencyValidator.validateGraph(
                Set.of(type(source), type(imported), type(unrelated)),
                List.of(new XsdDependency(type(source), imported.toString(), XsdDeclarationRole.TYPE, "Child member",
                        XsdDependencyType.TYPE_MEMBER_REFERENCE)),
                Set.of(SOURCE_NS));

        assertTrue(errors.isEmpty());
    }

    @Test
    void reportsUnresolvedReference() {
        RegistryKey source = RegistryKey.of(SOURCE_NS, "Child");

        List<String> errors = XsdDependencyValidator.validateGraph(
                Set.of(type(source)),
                List.of(new XsdDependency(type(source), "{" + IMPORTED_NS + "}Missing", XsdDeclarationRole.TYPE,
                        "Child base", XsdDependencyType.BASE_TYPE_REFERENCE)),
                Set.of(SOURCE_NS));

        assertTrue(errors.stream().anyMatch(error ->
                error.contains("unresolved required reference") && error.contains("expectedType=TYPE")));
    }

    @Test
    void reportsWrongDeclarationRoleSeparatelyFromMissingDeclaration() {
        RegistryKey sharedName = RegistryKey.of(SOURCE_NS, "SharedName");

        List<String> errors = XsdDependencyValidator.validateGraph(
                Set.of(new XsdDeclaration(sharedName, XsdDeclarationRole.ELEMENT)),
                List.of(new XsdDependency(
                        new XsdDeclaration(RegistryKey.of(SOURCE_NS, "UsesSharedName"), XsdDeclarationRole.TYPE),
                        sharedName.toString(),
                        XsdDeclarationRole.TYPE,
                        "complex type UsesSharedName content",
                        XsdDependencyType.TYPE_MEMBER_REFERENCE)),
                Set.of(SOURCE_NS));

        assertTrue(errors.stream().anyMatch(error ->
                error.contains("wrongTargetType")
                        && error.contains("expectedType=TYPE")
                        && error.contains("ELEMENT")));
    }

    @Test
    void rejectsInheritanceCycle() {
        RegistryKey first = RegistryKey.of(SOURCE_NS, "First");
        RegistryKey second = RegistryKey.of(SOURCE_NS, "Second");

        List<String> errors = XsdDependencyValidator.validateGraph(
                Set.of(type(first), type(second)),
                List.of(
                        new XsdDependency(
                                type(first),
                                second.toString(),
                                XsdDeclarationRole.TYPE,
                                "First base",
                                XsdDependencyType.BASE_TYPE_REFERENCE),
                        new XsdDependency(
                                type(second),
                                first.toString(),
                                XsdDeclarationRole.TYPE,
                                "Second base",
                                XsdDependencyType.BASE_TYPE_REFERENCE)),
                Set.of(SOURCE_NS));

        assertTrue(errors.stream().anyMatch(error ->
                error.contains("dependency cycle")
                        && error.contains(first.toString())
                        && error.contains(second.toString())));
    }

    @Test
    void acceptsSelfReferentialTypeMemberDependency() {
        RegistryKey portfolio = RegistryKey.of(SOURCE_NS, "Portfolio");

        List<String> errors = XsdDependencyValidator.validateGraph(
                Set.of(type(portfolio)),
                List.of(new XsdDependency(
                        type(portfolio),
                        portfolio.toString(),
                        XsdDeclarationRole.TYPE,
                        "Portfolio member",
                        XsdDependencyType.TYPE_MEMBER_REFERENCE)),
                Set.of(SOURCE_NS));

        assertTrue(errors.isEmpty(), () -> "Unexpected dependency errors: " + errors);
    }

    @Test
    void acceptsMutuallyRecursiveTypeMemberDependencies() {
        RegistryKey formula = RegistryKey.of(SOURCE_NS, "Formula");
        RegistryKey component = RegistryKey.of(SOURCE_NS, "FormulaComponent");

        List<String> errors = XsdDependencyValidator.validateGraph(
                Set.of(type(formula), type(component)),
                List.of(
                        new XsdDependency(
                                type(formula),
                                component.toString(),
                                XsdDeclarationRole.TYPE,
                                "Formula component member",
                                XsdDependencyType.TYPE_MEMBER_REFERENCE),
                        new XsdDependency(
                                type(component),
                                formula.toString(),
                                XsdDeclarationRole.TYPE,
                                "FormulaComponent formula member",
                                XsdDependencyType.TYPE_MEMBER_REFERENCE)),
                Set.of(SOURCE_NS));

        assertTrue(errors.isEmpty(), () -> "Unexpected dependency errors: " + errors);
    }

    @Test
    void rejectsSelfReferentialBaseTypeDependencyCycle() {
        RegistryKey portfolio = RegistryKey.of(SOURCE_NS, "Portfolio");

        List<String> errors = XsdDependencyValidator.validateGraph(
                Set.of(type(portfolio)),
                List.of(new XsdDependency(
                        type(portfolio),
                        portfolio.toString(),
                        XsdDeclarationRole.TYPE,
                        "Portfolio base",
                        XsdDependencyType.BASE_TYPE_REFERENCE)),
                Set.of(SOURCE_NS));

        assertTrue(errors.stream().anyMatch(error ->
                error.contains("dependency cycle") && error.contains(portfolio.toString())));
    }

    @Test
    void rejectsCrossNamespaceRecursiveTypeMemberDependencies() {
        RegistryKey source = RegistryKey.of(SOURCE_NS, "Source");
        RegistryKey imported = RegistryKey.of(IMPORTED_NS, "Imported");

        List<String> errors = XsdDependencyValidator.validateGraph(
                Set.of(type(source), type(imported)),
                List.of(
                        new XsdDependency(
                                type(source),
                                imported.toString(),
                                XsdDeclarationRole.TYPE,
                                "Source member",
                                XsdDependencyType.TYPE_MEMBER_REFERENCE),
                        new XsdDependency(
                                type(imported),
                                source.toString(),
                                XsdDeclarationRole.TYPE,
                                "Imported member",
                                XsdDependencyType.TYPE_MEMBER_REFERENCE)),
                Set.of(SOURCE_NS));

        assertTrue(errors.stream().anyMatch(error -> error.contains("dependency cycle")));
    }

    @Test
    void rejectsUnsupportedEdgeWithinRecursiveComponent() {
        RegistryKey first = RegistryKey.of(SOURCE_NS, "First");
        RegistryKey second = RegistryKey.of(SOURCE_NS, "Second");

        List<String> errors = XsdDependencyValidator.validateGraph(
                Set.of(type(first), type(second)),
                List.of(
                        new XsdDependency(
                                type(first),
                                second.toString(),
                                XsdDeclarationRole.TYPE,
                                "First member",
                                XsdDependencyType.TYPE_MEMBER_REFERENCE),
                        new XsdDependency(
                                type(first),
                                second.toString(),
                                XsdDeclarationRole.TYPE,
                                "First base",
                                XsdDependencyType.BASE_TYPE_REFERENCE),
                        new XsdDependency(
                                type(second),
                                first.toString(),
                                XsdDeclarationRole.TYPE,
                                "Second member",
                                XsdDependencyType.TYPE_MEMBER_REFERENCE)),
                Set.of(SOURCE_NS));

        assertTrue(errors.stream().anyMatch(error -> error.contains("dependency cycle")));
    }

    @Test
    void rejectsGroupDependencyCycle() {
        RegistryKey first = RegistryKey.of(SOURCE_NS, "FirstGroup");
        RegistryKey second = RegistryKey.of(SOURCE_NS, "SecondGroup");

        List<String> errors = XsdDependencyValidator.validateGraph(
                Set.of(
                        new XsdDeclaration(first, XsdDeclarationRole.GROUP),
                        new XsdDeclaration(second, XsdDeclarationRole.GROUP)),
                List.of(
                        new XsdDependency(
                                new XsdDeclaration(first, XsdDeclarationRole.GROUP),
                                second.toString(),
                                XsdDeclarationRole.GROUP,
                                "First group member",
                                XsdDependencyType.GROUP_REFERENCE),
                        new XsdDependency(
                                new XsdDeclaration(second, XsdDeclarationRole.GROUP),
                                first.toString(),
                                XsdDeclarationRole.GROUP,
                                "Second group member",
                                XsdDependencyType.GROUP_REFERENCE)),
                Set.of(SOURCE_NS));

        assertTrue(errors.stream().anyMatch(error -> error.contains("dependency cycle")));
    }

    @Test
    void rejectsMixedRoleDependencyCycle() {
        RegistryKey typeName = RegistryKey.of(SOURCE_NS, "TypeName");
        RegistryKey elementName = RegistryKey.of(SOURCE_NS, "ElementName");
        XsdDeclaration type = new XsdDeclaration(typeName, XsdDeclarationRole.TYPE);
        XsdDeclaration element = new XsdDeclaration(elementName, XsdDeclarationRole.ELEMENT);

        List<String> errors = XsdDependencyValidator.validateGraph(
                Set.of(type, element),
                List.of(
                        new XsdDependency(
                                type,
                                elementName.toString(),
                                XsdDeclarationRole.ELEMENT,
                                "type element member",
                                XsdDependencyType.ELEMENT_REFERENCE),
                        new XsdDependency(
                                element,
                                typeName.toString(),
                                XsdDeclarationRole.TYPE,
                                "element type",
                                XsdDependencyType.ELEMENT_REFERENCE)),
                Set.of(SOURCE_NS));

        assertTrue(errors.stream().anyMatch(error -> error.contains("dependency cycle")));
    }

    @Test
    void reportsUnresolvedDependencyInsideRecursiveGraph() {
        RegistryKey formula = RegistryKey.of(SOURCE_NS, "Formula");
        RegistryKey component = RegistryKey.of(SOURCE_NS, "FormulaComponent");

        List<String> errors = XsdDependencyValidator.validateGraph(
                Set.of(type(formula), type(component)),
                List.of(
                        new XsdDependency(
                                type(formula),
                                component.toString(),
                                XsdDeclarationRole.TYPE,
                                "Formula component member",
                                XsdDependencyType.TYPE_MEMBER_REFERENCE),
                        new XsdDependency(
                                type(component),
                                formula.toString(),
                                XsdDeclarationRole.TYPE,
                                "FormulaComponent formula member",
                                XsdDependencyType.TYPE_MEMBER_REFERENCE),
                        new XsdDependency(
                                type(component),
                                "{" + SOURCE_NS + "}Missing",
                                XsdDeclarationRole.TYPE,
                                "FormulaComponent missing member",
                                XsdDependencyType.TYPE_MEMBER_REFERENCE)),
                Set.of(SOURCE_NS));

        assertTrue(errors.stream().anyMatch(error -> error.contains("unresolved required reference")));
    }

    @Test
    void collectsQualifiedDependenciesFromInspectorModels() {
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
                        + "xmlns:tns=\"" + SOURCE_NS + "\" targetNamespace=\"" + SOURCE_NS + "\">"
                        + "<xs:simpleType name=\"Code\"><xs:restriction base=\"xs:NCName\"/></xs:simpleType>"
                        + "<xs:complexType name=\"Root\"><xs:sequence>"
                        + "<xs:element name=\"code\" type=\"tns:Code\"/>"
                        + "</xs:sequence></xs:complexType>"
                        + "<xs:element name=\"root\" type=\"tns:Root\"/>"
                        + "</xs:schema>"));

        List<String> errors = XsdDependencyValidator.validateGraph(
                inspector.getDeclaredDeclarations(),
                inspector.getRequiredDependencies(),
                Set.of(SOURCE_NS));

        assertTrue(errors.isEmpty(), () -> "Unexpected dependency errors: " + errors);
        assertTrue(inspector.getDeclaredDeclarations().stream()
                .map(XsdDeclaration::key)
                .anyMatch(RegistryKey.of(SOURCE_NS, "Code")::equals));
    }

    @Test
    void validatesQualifiedAndUnresolvedUnionMembersBeforeMapping() {
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
                        + "xmlns:tns=\"" + SOURCE_NS + "\" targetNamespace=\"" + SOURCE_NS + "\">"
                        + "<xs:simpleType name=\"Code\"><xs:restriction base=\"xs:string\"/></xs:simpleType>"
                        + "<xs:simpleType name=\"CodeUnion\"><xs:union memberTypes=\"Code missing:Missing\"/>"
                        + "</xs:simpleType>"
                        + "</xs:schema>"));

        List<XsdDependency> dependencies = inspector.getRequiredDependencies();
        assertTrue(dependencies.stream().anyMatch(dependency ->
                dependency.source().key().equals(RegistryKey.of(SOURCE_NS, "CodeUnion"))
                        && dependency.targetQName().equals("{" + SOURCE_NS + "}Code")
                        && dependency.context().contains("union member 'Code'")));
        assertTrue(dependencies.stream().anyMatch(dependency ->
                dependency.targetQName().equals("missing:Missing")
                        && dependency.context().contains("union member 'missing:Missing'")));

        List<String> errors = XsdDependencyValidator.validateGraph(
                inspector.getDeclaredDeclarations(), dependencies, Set.of(SOURCE_NS));

        assertTrue(errors.stream().anyMatch(error ->
                error.contains("invalid required reference 'missing:Missing'")
                        && error.contains("union member")));
    }

    @Test
    void doesNotTreatElementAndTypeWithSameNameAsCycle() {
        RegistryKey userRecord = RegistryKey.of(SOURCE_NS, "UserRecord");

        List<String> errors = XsdDependencyValidator.validateGraph(
                Set.of(
                        new XsdDeclaration(userRecord, XsdDeclarationRole.TYPE),
                        new XsdDeclaration(userRecord, XsdDeclarationRole.ELEMENT)),
                List.of(new XsdDependency(
                        new XsdDeclaration(userRecord, XsdDeclarationRole.ELEMENT),
                        userRecord.toString(),
                        XsdDeclarationRole.TYPE,
                        "element UserRecord type",
                        XsdDependencyType.ELEMENT_REFERENCE)),
                Set.of(SOURCE_NS));

        assertTrue(errors.isEmpty());
    }

    @Test
    void acceptsImportedGroupReferenceFromMultiImportFixture() {
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(Path.of(
                "src/test/resources/org/finos/rune/xsd/integration/features/inheritance/multi-importer.xsd")));

        List<String> errors = XsdDependencyValidator.validateGraph(
                inspector.getDeclaredDeclarations(),
                inspector.getRequiredDependencies(),
                Set.of("http://example.com/multi"));

        assertTrue(errors.isEmpty(), () -> "Unexpected dependency errors: " + errors);
    }

    @Test
    void acceptsLocalGroupReferenceWhenParserOmitsOwningSchemaLink() {
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(Path.of(
                "src/test/resources/org/finos/rune/xsd/integration/features/misc/composite-product.xsd")));

        List<String> errors = XsdDependencyValidator.validateGraph(
                inspector.getDeclaredDeclarations(),
                inspector.getRequiredDependencies(),
                Set.of("http://example.com/rune/complex"));

        assertTrue(errors.isEmpty(), () -> "Unexpected dependency errors: " + errors);
    }

    @Test
    void preservesDottedGroupNamesDuringDependencyValidation() {
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(Path.of(
                "src/test/resources/org/finos/rune/xsd/integration/features/groups/dotted-group-nesting.xsd")));

        assertTrue(inspector.getDeclaredDeclarations().contains(
                new XsdDeclaration(
                        RegistryKey.of("http://example.com/rune/test/groups/dotted", "Outer.model"),
                        XsdDeclarationRole.GROUP)));
        assertTrue(inspector.getDeclaredDeclarations().contains(
                new XsdDeclaration(
                        RegistryKey.of("http://example.com/rune/test/groups/dotted", "Inner.model"),
                        XsdDeclarationRole.GROUP)));

        List<String> errors = XsdDependencyValidator.validateGraph(
                inspector.getDeclaredDeclarations(),
                inspector.getRequiredDependencies(),
                Set.of("http://example.com/rune/test/groups/dotted"));

        assertTrue(errors.isEmpty(), () -> "Unexpected dependency errors: " + errors);
    }

    private static XsdDeclaration type(RegistryKey key) {
        return new XsdDeclaration(key, XsdDeclarationRole.TYPE);
    }
}
