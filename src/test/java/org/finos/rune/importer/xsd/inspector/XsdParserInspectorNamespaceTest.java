package org.finos.rune.importer.xsd.inspector;

import org.finos.rune.importer.xsd.inspector.model.InspectorAttributeNode;
import org.finos.rune.importer.xsd.inspector.model.InspectorChoiceNode;
import org.finos.rune.importer.xsd.inspector.model.InspectorGroupNode;
import org.finos.rune.importer.xsd.inspector.model.InspectorNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.namespace.QName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XsdParserInspectorNamespaceTest {

    private static final String ATTR_NS = "http://example.com/rune/attrref";
    private static final String BASE_NS = "http://example.com/base";
    private static final String COMMON_NS = "http://example.com/common";
    private static final String MULTI_NS = "http://example.com/multi";

    @Test
    void retainsGlobalAttributeNamespaceButLeavesLocalAttributeUnqualified() {
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(Path.of(
                "src/test/resources/org/finos/rune/xsd/integration/features/attributes/global-attribute-reference.xsd")));

        List<InspectorAttributeNode> attributes = inspector.getComplexTypeAttributeMembers(
                new QName(ATTR_NS, "GlobalAttributeReferenceType"));

        InspectorAttributeNode globalId = attributes.stream()
                .filter(attribute -> attribute.name().equals("globalId"))
                .findFirst()
                .orElseThrow();
        InspectorAttributeNode localFlag = attributes.stream()
                .filter(attribute -> attribute.name().equals("localFlag"))
                .findFirst()
                .orElseThrow();

        assertEquals(ATTR_NS, globalId.xmlNamespace());
        assertEquals(ATTR_NS + "/globalId", globalId.elementRefUri().orElseThrow());
        assertEquals("", localFlag.xmlNamespace());
    }

    @Test
    void leavesAttributeGroupMembersUnqualifiedWithoutElementReferences() {
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(Path.of(
                "src/test/resources/org/finos/rune/xsd/integration/features/attributes/attribute-group-reference.xsd")));

        List<InspectorAttributeNode> attributes = inspector.getComplexTypeAttributeMembers(
                new QName("http://example.com/rune/test/attrs/group", "AttrGroupType"));

        assertTrue(attributes.stream()
                .filter(attribute -> attribute.name().startsWith("ag") || attribute.name().equals("localAttr"))
                .allMatch(attribute -> "".equals(attribute.xmlNamespace()) && attribute.elementRefUri().isEmpty()));
    }

    @Test
    void resolvesAttributeGroupsAgainstTheirQualifiedReferenceNamespace(@TempDir Path directory) throws IOException {
        String rootNamespace = "urn:attribute-group:root";
        String importedNamespace = "urn:attribute-group:imported";
        Files.writeString(directory.resolve("root.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:imp="%s"
                           targetNamespace="%s">
                  <xs:import namespace="%s" schemaLocation="imported.xsd"/>
                  <xs:attributeGroup name="SharedAttributes">
                    <xs:attribute name="shared" type="xs:string"/>
                  </xs:attributeGroup>
                  <xs:complexType name="UsesImportedAttributes">
                    <xs:attributeGroup ref="imp:SharedAttributes"/>
                  </xs:complexType>
                </xs:schema>
                """.formatted(importedNamespace, rootNamespace, importedNamespace));
        Files.writeString(directory.resolve("imported.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="%s">
                  <xs:attributeGroup name="SharedAttributes">
                    <xs:attribute name="shared" type="xs:int"/>
                  </xs:attributeGroup>
                </xs:schema>
                """.formatted(importedNamespace));

        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(directory.resolve("root.xsd")));

        InspectorAttributeNode shared = inspector.getComplexTypeAttributeMembers(
                        new QName(rootNamespace, "UsesImportedAttributes"))
                .stream()
                .filter(attribute -> attribute.name().equals("shared"))
                .findFirst()
                .orElseThrow();

        assertEquals("{http://www.w3.org/2001/XMLSchema}int", shared.typeQName());
    }

    @Test
    void retainsOwningNamespaceForInheritedTypeMembers() {
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(Path.of(
                "src/test/resources/org/finos/rune/xsd/integration/features/inheritance/importer.xsd")));

        List<InspectorNode> members = inspector.getComplexTypeStructuredMembers(new QName(BASE_NS, "Person"));

        assertTrue(members.stream()
                .filter(InspectorAttributeNode.class::isInstance)
                .map(InspectorAttributeNode.class::cast)
                .allMatch(member -> BASE_NS.equals(member.xmlNamespace())));
    }

    @Test
    void retainsImportedGroupMemberNamespaceWhenGroupIsInspected() {
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(Path.of(
                "src/test/resources/org/finos/rune/xsd/integration/features/inheritance/multi-importer.xsd")));

        List<InspectorNode> groupMembers = inspector.getGroupStructuredMembers(new QName(COMMON_NS, "AuditGroup"));

        assertTrue(groupMembers.stream()
                .filter(InspectorAttributeNode.class::isInstance)
                .map(InspectorAttributeNode.class::cast)
                .allMatch(member -> COMMON_NS.equals(member.xmlNamespace())));

        List<InspectorNode> userMembers = inspector.getComplexTypeStructuredMembers(new QName(MULTI_NS, "UserRecord"));
        InspectorChoiceNode choice = userMembers.stream()
                .filter(InspectorChoiceNode.class::isInstance)
                .map(InspectorChoiceNode.class::cast)
                .findFirst()
                .orElseThrow();

        InspectorGroupNode group = choice.options().stream()
                .filter(InspectorGroupNode.class::isInstance)
                .map(InspectorGroupNode.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("{" + COMMON_NS + "}AuditGroup", group.groupTypeQName());

        InspectorAttributeNode auditCode = inspector.getComplexTypeAttributeMembers(new QName(MULTI_NS, "UserRecord")).stream()
                .filter(attribute -> attribute.name().equals("auditCode"))
                .findFirst()
                .orElseThrow();
        assertEquals(COMMON_NS, auditCode.xmlNamespace());
        assertEquals(COMMON_NS + "/auditCode", auditCode.elementRefUri().orElseThrow());
    }

    @Test
    void preservesGlobalElementReferenceInsideNestedGroupContent() {
        String namespace = "http://example.com/rune/test/groups/nesting";
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(Path.of(
                "src/test/resources/org/finos/rune/xsd/integration/features/groups/group-nesting.xsd")));

        InspectorAttributeNode baseCode = inspector.getGroupStructuredMembers(new QName(namespace, "BaseFields")).stream()
                .filter(InspectorAttributeNode.class::isInstance)
                .map(InspectorAttributeNode.class::cast)
                .filter(member -> member.name().equals("baseCode"))
                .findFirst()
                .orElseThrow();

        assertEquals("{" + namespace + "}baseCode", baseCode.typeQName());
        assertEquals(namespace + "/baseCode", baseCode.elementRefUri().orElseThrow());

        InspectorAttributeNode localBaseString = inspector.getGroupStructuredMembers(new QName(namespace, "BaseFields")).stream()
                .filter(InspectorAttributeNode.class::isInstance)
                .map(InspectorAttributeNode.class::cast)
                .filter(member -> member.name().equals("baseString"))
                .findFirst()
                .orElseThrow();
        assertEquals("{http://www.w3.org/2001/XMLSchema}string", localBaseString.typeQName());
        assertTrue(localBaseString.elementRefUri().isEmpty());
    }
}
