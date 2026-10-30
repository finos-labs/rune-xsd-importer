package org.finos.rune.importer.xsd.mapper.generation;

import com.regnosys.rosetta.rosetta.RosettaCardinality;
import com.regnosys.rosetta.rosetta.RosettaFactory;
import com.regnosys.rosetta.rosetta.RosettaType;
import com.regnosys.rosetta.rosetta.simple.Annotation;
import com.regnosys.rosetta.rosetta.expression.ChoiceOperation;
import com.regnosys.rosetta.rosetta.expression.Necessity;
import com.regnosys.rosetta.rosetta.expression.OneOfOperation;
import com.regnosys.rosetta.rosetta.simple.Attribute;
import com.regnosys.rosetta.rosetta.simple.Condition;
import com.regnosys.rosetta.rosetta.simple.Data;
import com.regnosys.rosetta.rosetta.simple.SimpleFactory;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.inspector.XsdUtils;
import org.finos.rune.importer.xsd.mapper.DataMapper;
import org.finos.rune.importer.xsd.mapper.context.MappingContext;
import org.finos.rune.importer.xsd.mapper.context.MappingSession;
import org.finos.rune.importer.xsd.mapper.resolution.MappingResolutionException;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.finos.rune.importer.xsd.registry.RegistryTestUtils;
import org.finos.rune.importer.xsd.registry.RuneTypeRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DataMapperComponentTest {

    @Test
    void emitsRequiredChoiceAndRelaxesBranchCardinality() {
        Data data = data("ChoiceData");
        Attribute first = attribute("first", 1, 1);
        Attribute second = attribute("second", 1, 1);
        data.getAttributes().addAll(List.of(first, second));

        new ConditionMapper().addChoiceCondition(
                data,
                List.of("first", "second"),
                1,
                true,
                false);

        Condition condition = data.getConditions().getFirst();
        ChoiceOperation operation = assertInstanceOf(ChoiceOperation.class, condition.getExpression());
        assertEquals(Necessity.REQUIRED, operation.getNecessity());
        assertEquals(List.of(first, second), operation.getAttributes());
        assertEquals(0, first.getCard().getInf());
        assertEquals(0, second.getCard().getInf());
    }

    @Test
    void emitsOneOfForMixedUnionMembers() {
        Data data = data("UnionData");
        Attribute first = attribute("first", 0, 1);
        Attribute second = attribute("second", 0, 1);
        data.getAttributes().addAll(List.of(first, second));

        new ConditionMapper().addChoiceCondition(
                data,
                List.of("first", "second"),
                1,
                true,
                true);

        assertInstanceOf(OneOfOperation.class, data.getConditions().getFirst().getExpression());
    }

    @Test
    void registersCaseOnlyGlobalElementAsQualifiedAliasOfItsComplexType() {
        String namespace = "http://example.com/global";
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(
                Path.of("src/test/resources/org/finos/rune/xsd/integration/features/misc/global-element.xsd")));
        RuneTypeRegistry registry = new TestRegistry();

        List<Data> created = new DataMapper(new MappingSession()).register(inspector, namespace, registry);

        Data complexType = assertInstanceOf(Data.class, registry.resolve(RegistryKey.of(namespace, "SimpleXSType")));
        assertSame(complexType, registry.resolve(RegistryKey.of(namespace, "simpleXsType")));
        assertEquals(1, created.stream().filter(data -> "SimpleXSType".equals(data.getName())).count());
    }

    @Test
    void createsUnboundedAttributeAndSuppressesInheritedDuplicate() {
        RuneTypeRegistry registry = new TestRegistry();
        RosettaType stringType = RosettaFactory.eINSTANCE.createRosettaTypeAlias();
        stringType.setName("string");
        registry.register(RegistryKey.of("http://www.w3.org/2001/XMLSchema", "string"), stringType);

        MappingContext context = MappingContext.of(
                inspector(),
                "http://example.com/source",
                registry,
                new MappingSession());
        Data parent = data("Parent");
        parent.getAttributes().add(attribute("value", 0, 1));
        Data child = data("Child");
        child.setSuperType(parent);

        DataAttributeMapper attributeMapper = new DataAttributeMapper();
        Attribute created = attributeMapper.map(
                child,
                "items",
                "{http://www.w3.org/2001/XMLSchema}string",
                0,
                -1,
                "Items",
                context,
                Optional.empty());
        Attribute duplicate = attributeMapper.map(
                child,
                "value",
                "{http://www.w3.org/2001/XMLSchema}string",
                0,
                1,
                "Inherited value",
                context,
                Optional.empty());

        assertEquals("items", created.getName());
        assertEquals(0, created.getCard().getInf());
        assertTrue(created.getCard().isUnbounded());
        assertNull(duplicate);
    }

    @Test
    void rejectsUnresolvedAttributeTypeBeforeMutatingTheTarget() {
        RuneTypeRegistry registry = new TestRegistry();
        MappingContext context = MappingContext.of(
                inspector(),
                "http://example.com/source",
                registry,
                new MappingSession());
        Data data = data("StrictData");

        MappingResolutionException failure = assertThrows(
                MappingResolutionException.class,
                () -> new DataAttributeMapper().map(
                        data,
                        "missing",
                        "{http://example.com/source}MissingType",
                        1,
                        1,
                        "Source documentation",
                        context,
                        Optional.empty()));

        assertTrue(failure.getMessage().contains("MissingType"));
        assertTrue(data.getAttributes().isEmpty());
    }

    @Test
    void rejectsDistinctAttributesThatNormalizeToTheSameRuneName() {
        RuneTypeRegistry registry = new TestRegistry();
        RosettaType stringType = RosettaFactory.eINSTANCE.createRosettaTypeAlias();
        stringType.setName("string");
        registry.register(RegistryKey.of("http://www.w3.org/2001/XMLSchema", "string"), stringType);

        MappingContext context = MappingContext.of(
                inspector(),
                "http://example.com/source",
                registry,
                new MappingSession());
        Data data = data("CollisionData");
        DataAttributeMapper firstMapper = new DataAttributeMapper();

        firstMapper.map(data, "a-b", "{http://www.w3.org/2001/XMLSchema}string", 0, 1,
                "First", context, Optional.empty());

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new DataAttributeMapper().map(data, "a_b", "{http://www.w3.org/2001/XMLSchema}string", 0, 1,
                        "Second", context, Optional.empty()));

        assertTrue(failure.getMessage().contains("a-b"));
        assertTrue(failure.getMessage().contains("a_b"));
        assertTrue(failure.getMessage().contains("aB"));
        assertEquals(1, data.getAttributes().size());
    }

    private Data data(String name) {
        Data data = SimpleFactory.eINSTANCE.createData();
        data.setName(name);
        return data;
    }

    private Attribute attribute(String name, int min, int max) {
        Attribute attribute = SimpleFactory.eINSTANCE.createAttribute();
        attribute.setName(name);
        RosettaCardinality cardinality = RosettaFactory.eINSTANCE.createRosettaCardinality();
        cardinality.setInf(min);
        cardinality.setSup(max);
        attribute.setCard(cardinality);
        return attribute;
    }

    private XsdParserInspector inspector() {
        return new XsdParserInspector(XsdUtils.parseXsd(
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>"));
    }

    private static final class TestRegistry extends RuneTypeRegistry {

        private final Map<RegistryKey, RosettaType> types = new HashMap<>();

        private TestRegistry() {
            super(RegistryTestUtils.completeBuiltinRegistry());
        }

        @Override
        public void register(RegistryKey key, RosettaType type) {
            types.put(key, type);
        }

        @Override
        public RosettaType resolve(RegistryKey key) {
            return types.get(key);
        }

        @Override
        public Annotation resolveAnnotation(String name) {
            Annotation annotation = SimpleFactory.eINSTANCE.createAnnotation();
            annotation.setName(name);
            return annotation;
        }
    }
}
