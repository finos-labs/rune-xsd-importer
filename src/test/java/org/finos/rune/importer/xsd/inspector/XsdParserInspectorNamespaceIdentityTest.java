package org.finos.rune.importer.xsd.inspector;

import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.core.XsdDependencyValidator;
import org.finos.rune.importer.xsd.inspector.model.InspectorGroupNode;
import org.finos.rune.importer.xsd.mapper.serialization.XmlConfigurationIndex;
import org.finos.rune.importer.xsd.mapper.resolution.MappingResolutionException;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;

import static org.junit.jupiter.api.Assertions.*;

class XsdParserInspectorNamespaceIdentityTest {

    private static final String ROOT_NS = "urn:identity:root";
    private static final String IMPORTED_NS = "urn:identity:imported";
    private static final String SECOND_IMPORTED_NS = "urn:identity:second-imported";

    @Test
    void unknownPrefixDoesNotRecoverAComplexTypeByLocalName(@TempDir Path directory) throws IOException {
        inspectorWithImportedSchemas(directory, rootSchema(
                "<xs:complexType name=\"SharedComplex\"><xs:sequence/></xs:complexType>"
                        + "<xs:complexType name=\"UsesSharedComplex\"><xs:sequence>"
                        + "<xs:element name=\"value\" type=\"missing:SharedComplex\"/>"
                        + "</xs:sequence></xs:complexType>"));
        XsdParserInspectorHelper helper = helper(directory);

        String resolved = helper.resolveTypeQName(null, "missing:SharedComplex", ROOT_NS, null);

        assertNotNull(helper.findGlobalComplexType(ROOT_NS, "SharedComplex"));
        assertNotNull(helper.findGlobalComplexType(IMPORTED_NS, "SharedComplex"));
        assertNotEquals("{" + ROOT_NS + "}SharedComplex", resolved);
        assertNotEquals("{" + IMPORTED_NS + "}SharedComplex", resolved);
        assertNull(RegistryKey.parse(resolved));
    }

    @Test
    void unknownPrefixDoesNotRecoverASimpleTypeByLocalName(@TempDir Path directory) throws IOException {
        inspectorWithImportedSchemas(directory, rootSchema(
                "<xs:simpleType name=\"SharedSimple\"><xs:restriction base=\"xs:string\"/></xs:simpleType>"
                        + "<xs:element name=\"value\" type=\"missing:SharedSimple\"/>"));
        XsdParserInspectorHelper helper = helper(directory);

        String resolved = helper.resolveTypeQName(null, "missing:SharedSimple", ROOT_NS, null);

        assertNotNull(helper.findGlobalSimpleType(ROOT_NS, "SharedSimple"));
        assertNotNull(helper.findGlobalSimpleType(IMPORTED_NS, "SharedSimple"));
        assertNull(RegistryKey.parse(resolved));
    }

    @Test
    void explicitQualifiedReferenceUsesOnlyTheMatchingNamespace(@TempDir Path directory) throws IOException {
        inspectorWithImportedSchemas(directory, rootSchema(
                "<xs:complexType name=\"UsesSharedComplex\"><xs:sequence>"
                        + "<xs:element name=\"value\" type=\"imp:SharedComplex\"/>"
                        + "</xs:sequence></xs:complexType>"));
        XsdParserInspectorHelper helper = helper(directory);

        String resolved = helper.resolveTypeQName(null, "imp:SharedComplex", ROOT_NS, null);

        assertEquals("{" + IMPORTED_NS + "}SharedComplex", resolved);
    }

    @Test
    void importedElementTypeUsesCanonicalQualifiedQName(@TempDir Path directory) throws IOException {
        XsdParserInspector inspector = inspectorWithImportedSchemas(directory, rootSchema(
                "<xs:element name=\"usesReference\" type=\"imp:ReferenceType\"/>"));

        assertEquals(RegistryKey.of(IMPORTED_NS, "ReferenceType"),
                inspector.getElementTypeKey(new QName(IMPORTED_NS, "Reference")));
    }

    @Test
    void redundantPrefixInsideClarkQNameIsNormalizedWithoutNamespaceFallback() {
        XsdParserInspectorHelper helper = new XsdParserInspectorHelper(XsdUtils.parseXsd(
                rootSchemaWithoutImports("<xs:element name=\"value\" type=\"xs:string\"/>")));

        assertEquals("{" + IMPORTED_NS + "}ReferenceType",
                helper.resolveTypeQName(
                        null,
                        "{" + IMPORTED_NS + "}imp:ReferenceType",
                        ROOT_NS,
                        null));
    }

    @Test
    void qualifiedFacadeLookupPreservesTheRawQName(@TempDir Path directory) throws IOException {
        XsdParserInspector inspector = inspectorWithImportedSchemas(directory, rootSchema(
                "<xs:element name=\"value\" type=\"imp:SharedComplex\"/>"));

        assertEquals(RegistryKey.of(IMPORTED_NS, "SharedComplex"),
                inspector.getElementTypeKey(new QName(ROOT_NS, "value")));
    }

    @Test
    void missingFacadeElementIsReportedAsUnresolved() {
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(
                rootSchemaWithoutImports("<xs:element name=\"value\" type=\"xs:string\"/>")));

        QName missing = new QName(ROOT_NS, "missing");
        assertNull(inspector.getInlineProxyTypeKey(missing));
        assertTrue(inspector.getElementInlineAttributeMembers(missing).isEmpty());
    }

    @Test
    void attributeOnlyInlineComplexTypeIsNotAnInlineGroupProxy() {
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(
                rootSchemaWithoutImports("<xs:element name=\"attributeOnly\"><xs:complexType>"
                        + "<xs:attribute name=\"code\" type=\"xs:string\"/>"
                        + "</xs:complexType></xs:element>")));

        assertNull(inspector.getInlineProxyTypeKey(new QName(ROOT_NS, "attributeOnly")));
    }

    @Test
    void unqualifiedReferenceUsesTheExplicitSchemaContext(@TempDir Path directory) throws IOException {
        inspectorWithImportedSchemas(directory, rootSchema(
                "<xs:complexType name=\"SharedComplex\"><xs:sequence/></xs:complexType>"
                        + "<xs:complexType name=\"UsesSharedComplex\"><xs:sequence>"
                        + "<xs:element name=\"value\" type=\"SharedComplex\"/>"
                        + "</xs:sequence></xs:complexType>"));
        XsdParserInspectorHelper helper = helper(directory);

        String resolved = helper.resolveTypeQName(null, "SharedComplex", ROOT_NS, null);

        assertEquals("{" + ROOT_NS + "}SharedComplex", resolved);
    }

    @Test
    void ambiguousLocalNameReferenceFailsClosed(@TempDir Path directory) throws IOException {
        XsdParserInspector inspector = inspectorWithImportedSchemas(directory, rootSchema(
                "<xs:complexType name=\"UsesAmbiguous\"><xs:sequence>"
                        + "<xs:element name=\"value\" type=\"AmbiguousComplex\"/>"
                        + "</xs:sequence></xs:complexType>"),
                "second-import.xsd");

        MappingResolutionException error = assertThrows(MappingResolutionException.class, () -> XsdDependencyValidator.validate(
                inspector,
                new RuneGeneratorConfig().addTargetNamespace(ROOT_NS)));
        assertTrue(error.getMessage().contains("AmbiguousComplex"));
    }

    @Test
    void malformedQNameFailsClosed() {
        XsdParserInspectorHelper helper = new XsdParserInspectorHelper(XsdUtils.parseXsd(
                rootSchemaWithoutImports("<xs:element name=\"value\" type=\"xs:string\"/>")));

        String targetQName = helper.resolveTypeQName(null, "bad:", ROOT_NS, null);

        assertNull(RegistryKey.parse(targetQName));
    }

    @Test
    void unknownPrefixFailsClosedEvenWhenLocalNameExistsInSourceNamespace(@TempDir Path directory) throws IOException {
        inspectorWithImportedSchemas(directory, rootSchema(
                "<xs:complexType name=\"SharedComplex\"><xs:sequence/></xs:complexType>"
                        + "<xs:complexType name=\"UsesSharedComplex\"><xs:sequence>"
                        + "<xs:element name=\"value\" type=\"missing:SharedComplex\"/>"
                        + "</xs:sequence></xs:complexType>"));
        XsdParserInspectorHelper helper = helper(directory);

        assertNull(RegistryKey.parse(helper.resolveTypeQName(null, "missing:SharedComplex", ROOT_NS, null)));
    }

    @Test
    void explicitNonXsdNamespaceDoesNotBecomeBuiltinByLocalName() {
        XsdParserInspectorHelper helper = new XsdParserInspectorHelper(XsdUtils.parseXsd(
                rootSchemaWithoutImports("<xs:element name=\"value\" type=\"xs:string\"/>")));

        String resolved = helper.resolveTypeQName(null, "missing:string", ROOT_NS, null);

        assertNull(RegistryKey.parse(resolved));
    }

    @Test
    void missingNamespaceContextDoesNotTriggerGlobalRecovery(@TempDir Path directory) throws IOException {
        XsdParserInspector inspector = inspectorWithImportedSchemas(directory, rootSchema(
                "<xs:complexType name=\"UsesSharedComplex\"><xs:sequence/></xs:complexType>"));
        XsdParserInspectorHelper helper = new XsdParserInspectorHelper(
                XsdUtils.parseXsd(directory.resolve("root.xsd")));

        String resolved = helper.resolveTypeQName(null, "SharedComplex", null, null);

        assertNotEquals("{" + IMPORTED_NS + "}SharedComplex", resolved);
        RegistryKey key = RegistryKey.parse(resolved);
        assertTrue(key == null || inspector.getDeclaredDeclarations().stream()
                .noneMatch(declaration -> declaration.key().equals(key)));
    }

    @Test
    void declarationRolesRemainDistinctForEqualLocalNames() {
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(
                rootSchemaWithoutImports(
                        "<xs:complexType name=\"SameName\"><xs:sequence/></xs:complexType>"
                                + "<xs:element name=\"SameName\" type=\"tns:SameName\"/>"
                                + "<xs:group name=\"SameName\"><xs:sequence/></xs:group>")));

        assertEquals(List.of("SameName"), inspector.getAllGlobalComplexTypes(ROOT_NS));
        assertEquals(List.of("SameName"), inspector.getAllGlobalElements(ROOT_NS));
        assertEquals(List.of("SameName"), inspector.getAllGlobalGroups(ROOT_NS));
        assertTrue(inspector.isGlobalComplexType(RegistryKey.of(ROOT_NS, "SameName")));
        assertFalse(inspector.isGlobalComplexType(RegistryKey.of(ROOT_NS, "Missing")));
        assertTrue(inspector.isGlobalElement(ROOT_NS, "SameName"));
        assertFalse(inspector.isGlobalElement(ROOT_NS, "Missing"));

        assertTrue(inspector.getDeclaredDeclarations().contains(
                new XsdDeclaration(RegistryKey.of(ROOT_NS, "SameName"), XsdDeclarationRole.TYPE)));
        assertTrue(inspector.getDeclaredDeclarations().contains(
                new XsdDeclaration(RegistryKey.of(ROOT_NS, "SameName"), XsdDeclarationRole.ELEMENT)));
        assertTrue(inspector.getDeclaredDeclarations().contains(
                new XsdDeclaration(RegistryKey.of(ROOT_NS, "SameName"), XsdDeclarationRole.GROUP)));
    }

    @Test
    void importedGroupResolutionKeepsItsQualifiedIdentity(@TempDir Path directory) throws IOException {
        XsdParserInspector inspector = inspectorWithImportedSchemas(directory, rootSchema(
                "<xs:complexType name=\"UsesGroup\"><xs:sequence>"
                        + "<xs:group ref=\"imp:SharedGroup\"/>"
                        + "</xs:sequence></xs:complexType>"));

        InspectorGroupNode group = inspector.getComplexTypeStructuredMembers(new QName(ROOT_NS, "UsesGroup"))
                .stream()
                .filter(InspectorGroupNode.class::isInstance)
                .map(InspectorGroupNode.class::cast)
                .findFirst()
                .orElseThrow();

        assertEquals("{" + IMPORTED_NS + "}SharedGroup", group.groupTypeQName());
    }

    @Test
    void substitutionGroupHeadUsesTheOwningSchemaPrefixContext(@TempDir Path directory) throws IOException {
        XsdParserInspector inspector = inspectorWithImportedSchemas(directory, rootSchema(
                "<xs:element name=\"Member\" type=\"imp:ReferenceType\" substitutionGroup=\"imp:Reference\"/>"));
        RegistryKey member = RegistryKey.of(ROOT_NS, "Member");
        RegistryKey expectedHead = RegistryKey.of(IMPORTED_NS, "Reference");

        assertEquals(expectedHead, inspector.getElementSubstitutionGroupKey(new QName(ROOT_NS, "Member")));
        assertEquals(expectedHead, new XmlConfigurationIndex(inspector).substitutionGroup(member));
        assertTrue(inspector.getRequiredDependencies().stream().anyMatch(dependency ->
                dependency.source().key().equals(member)
                        && dependency.targetQName().equals(expectedHead.toString())
                        && dependency.targetRole() == XsdDeclarationRole.ELEMENT));
    }

    @Test
    void xmlConfigurationPrefixCacheDisambiguatesCollidingImportedPrefixes(@TempDir Path directory) throws IOException {
        String firstNamespace = "urn:identity:prefix:first";
        String secondNamespace = "urn:identity:prefix:second";
        Files.writeString(directory.resolve("root.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:tns="urn:identity:root"
                           targetNamespace="urn:identity:root">
                  <xs:import namespace="urn:identity:prefix:first" schemaLocation="first.xsd"/>
                  <xs:import namespace="urn:identity:prefix:second" schemaLocation="second.xsd"/>
                </xs:schema>
                """);
        Files.writeString(directory.resolve("first.xsd"), schemaWithPrefix(firstNamespace, "First"));
        Files.writeString(directory.resolve("second.xsd"), schemaWithPrefix(secondNamespace, "Second"));

        XmlConfigurationIndex index = new XmlConfigurationIndex(
                new XsdParserInspector(XsdUtils.parseXsd(directory.resolve("root.xsd"))));
        String firstPrefix = index.prefixForNamespace(firstNamespace, ROOT_NS);
        String secondPrefix = index.prefixForNamespace(secondNamespace, ROOT_NS);
        Map<String, String> prefixes = index.namespacePrefixes(ROOT_NS);

        assertNotEquals(firstPrefix, secondPrefix);
        assertEquals(firstNamespace, prefixes.get(firstPrefix));
        assertEquals(secondNamespace, prefixes.get(secondPrefix));
    }

    @Test
    void unionMemberPrefixUsesTheOwningIncludedSchemaContext(@TempDir Path directory) throws IOException {
        String firstUnionNamespace = "urn:identity:union:first";
        String secondUnionNamespace = "urn:identity:union:second";
        Files.writeString(directory.resolve("root.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:tns="urn:identity:root"
                           xmlns:member="urn:identity:union:first"
                           targetNamespace="urn:identity:root">
                  <xs:import namespace="urn:identity:union:first" schemaLocation="first.xsd"/>
                  <xs:import namespace="urn:identity:union:second" schemaLocation="second.xsd"/>
                  <xs:include schemaLocation="union.xsd"/>
                </xs:schema>
                """);
        Files.writeString(directory.resolve("union.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:member="urn:identity:union:second"
                           targetNamespace="urn:identity:root">
                  <xs:simpleType name="CodeUnion">
                    <xs:union memberTypes="member:Code"/>
                  </xs:simpleType>
                </xs:schema>
                """);
        Files.writeString(directory.resolve("first.xsd"), enumSchema(firstUnionNamespace, "FIRST"));
        Files.writeString(directory.resolve("second.xsd"), enumSchema(secondUnionNamespace, "SECOND"));

        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(directory.resolve("root.xsd")));
        QName unionName = new QName(ROOT_NS, "CodeUnion");

        assertEquals(List.of("{" + secondUnionNamespace + "}Code"), inspector.getUnionMemberTypes(unionName));
        assertTrue(inspector.getRequiredDependencies().stream().anyMatch(dependency ->
                dependency.source().key().equals(RegistryKey.of(ROOT_NS, "CodeUnion"))
                        && dependency.targetQName().equals("{" + secondUnionNamespace + "}Code")));
        assertDoesNotThrow(() -> XsdDependencyValidator.validate(
                inspector, new RuneGeneratorConfig().addTargetNamespace(ROOT_NS)));
        assertTrue(inspector.isUnionOfEnums(unionName));
        assertEquals(List.of("SECOND"), inspector.getUnionEnumValues(unionName));
    }

    @Test
    void nestedEnumUnionsAreClassifiedAndFlattenedOnce() {
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(rootSchemaWithoutImports(
                "<xs:simpleType name=\"Code\"><xs:restriction base=\"xs:string\">"
                        + "<xs:enumeration value=\"A\"/></xs:restriction></xs:simpleType>"
                        + "<xs:simpleType name=\"Intermediate\"><xs:union memberTypes=\"tns:Code\"/>"
                        + "</xs:simpleType>"
                        + "<xs:simpleType name=\"Combined\"><xs:union memberTypes=\"tns:Intermediate\"/>"
                        + "</xs:simpleType>")));

        QName unionName = new QName(ROOT_NS, "Combined");

        assertTrue(inspector.isUnionOfEnums(unionName));
        assertEquals(List.of("A"), inspector.getUnionEnumValues(unionName));
    }

    private static XsdParserInspector inspectorWithImportedSchemas(
            Path directory,
            String rootBody) throws IOException {
        return inspectorWithImportedSchemas(directory, rootBody, "other.xsd");
    }

    private static XsdParserInspector inspectorWithImportedSchemas(
            Path directory,
            String rootBody,
            String secondImportFile) throws IOException {
        Files.writeString(directory.resolve("root.xsd"), rootSchemaWithImports(rootBody, secondImportFile));
        Files.writeString(directory.resolve("other.xsd"), importedSchema(IMPORTED_NS, "other.xsd"));
        if ("second-import.xsd".equals(secondImportFile)) {
            Files.writeString(directory.resolve(secondImportFile), importedSchema(SECOND_IMPORTED_NS, secondImportFile));
        }
        return new XsdParserInspector(XsdUtils.parseXsd(directory.resolve("root.xsd")));
    }

    private static XsdParserInspectorHelper helper(Path directory) {
        return new XsdParserInspectorHelper(XsdUtils.parseXsd(directory.resolve("root.xsd")));
    }

    private static String rootSchema(String body) {
        return body;
    }

    private static String rootSchemaWithImports(String body, String secondImportFile) {
        String secondImport = "second-import.xsd".equals(secondImportFile)
                ? "<xs:import namespace=\"" + SECOND_IMPORTED_NS + "\" schemaLocation=\"second-import.xsd\"/>"
                : "";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""
                + " xmlns:tns=\"" + ROOT_NS + "\" xmlns:imp=\"" + IMPORTED_NS + "\""
                + " targetNamespace=\"" + ROOT_NS + "\" elementFormDefault=\"qualified\">"
                + "<xs:import namespace=\"" + IMPORTED_NS + "\" schemaLocation=\"other.xsd\"/>"
                + secondImport
                + body
                + "</xs:schema>";
    }

    private static String rootSchemaWithoutImports(String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""
                + " xmlns:tns=\"" + ROOT_NS + "\" targetNamespace=\"" + ROOT_NS + "\">"
                + body
                + "</xs:schema>";
    }

    private static String importedSchema(String namespace, String fileName) {
        String declarations = namespace.equals(IMPORTED_NS)
                ? "<xs:complexType name=\"SharedComplex\"><xs:sequence/></xs:complexType>"
                        + "<xs:simpleType name=\"SharedSimple\"><xs:restriction base=\"xs:string\"/></xs:simpleType>"
                        + "<xs:group name=\"SharedGroup\"><xs:sequence/></xs:group>"
                        + "<xs:element name=\"Reference\" type=\"imp:ReferenceType\"/>"
                        + "<xs:complexType name=\"ReferenceType\"><xs:sequence/></xs:complexType>"
                        + "<xs:complexType name=\"AmbiguousComplex\"><xs:sequence/></xs:complexType>"
                : "<xs:complexType name=\"AmbiguousComplex\"><xs:sequence/></xs:complexType>";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""
                + " xmlns:imp=\"" + namespace + "\""
                + " targetNamespace=\"" + namespace + "\">"
                + declarations
                + "</xs:schema>";
    }

    private static String enumSchema(String namespace, String value) {
        return """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="%s">
                  <xs:simpleType name="Code">
                    <xs:restriction base="xs:string">
                      <xs:enumeration value="%s"/>
                    </xs:restriction>
                  </xs:simpleType>
                </xs:schema>
                """.formatted(namespace, value);
    }

    private static String schemaWithPrefix(String namespace, String typeName) {
        return """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:shared="%s"
                           targetNamespace="%s">
                  <xs:complexType name="%s"><xs:sequence/></xs:complexType>
                </xs:schema>
                """.formatted(namespace, namespace, typeName);
    }
}
