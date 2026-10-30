package org.finos.rune.importer.xsd.mapper.generation;

import com.regnosys.rosetta.rosetta.RosettaFactory;
import com.regnosys.rosetta.rosetta.RosettaTypeAlias;
import com.regnosys.rosetta.rosetta.TypeCall;
import com.regnosys.rosetta.rosetta.TypeParameter;
import com.regnosys.rosetta.rosetta.expression.ExpressionFactory;
import com.regnosys.rosetta.rosetta.expression.RosettaIntLiteral;
import com.regnosys.rosetta.rosetta.expression.RosettaNumberLiteral;
import org.finos.rune.importer.xsd.inspector.model.InspectorSimpleTypeNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RestrictionMapperTest {

    @Test
    void mapsIntegralAndDecimalBoundsAccordingToTheirLexicalShape() {
        RosettaFactory runeFactory = RosettaFactory.eINSTANCE;
        RosettaTypeAlias alias = runeFactory.createRosettaTypeAlias();
        alias.setName("Amount");

        RosettaTypeAlias baseType = runeFactory.createRosettaTypeAlias();
        baseType.setName("number");
        addParameter(baseType, "min", runeFactory);
        addParameter(baseType, "max", runeFactory);

        TypeCall typeCall = runeFactory.createTypeCall();
        typeCall.setType(baseType);
        alias.setTypeCall(typeCall);

        InspectorSimpleTypeNode node = new InspectorSimpleTypeNode(
                "{http://www.w3.org/2001/XMLSchema}decimal",
                List.of(),
                null,
                null,
                "0",
                "10.50",
                null,
                null);

        new RestrictionMapper(ExpressionFactory.eINSTANCE, runeFactory).map(alias, node, false);

        assertEquals(2, typeCall.getArguments().size());
        RosettaIntLiteral minimum = assertInstanceOf(
                RosettaIntLiteral.class,
                typeCall.getArguments().get(0).getValue());
        RosettaNumberLiteral maximum = assertInstanceOf(
                RosettaNumberLiteral.class,
                typeCall.getArguments().get(1).getValue());
        assertEquals(BigInteger.ZERO, minimum.getValue());
        assertEquals(new BigDecimal("10.50"), maximum.getValue());
    }

    @Test
    void representsAnEmptyStringEnumerationAsZeroMaximumLength() {
        RosettaFactory runeFactory = RosettaFactory.eINSTANCE;
        RosettaTypeAlias alias = runeFactory.createRosettaTypeAlias();
        alias.setName("EmptyValue");

        RosettaTypeAlias baseType = runeFactory.createRosettaTypeAlias();
        baseType.setName("string");
        addParameter(baseType, "maxLength", runeFactory);

        TypeCall typeCall = runeFactory.createTypeCall();
        typeCall.setType(baseType);
        alias.setTypeCall(typeCall);

        InspectorSimpleTypeNode node = new InspectorSimpleTypeNode(
                "{http://www.w3.org/2001/XMLSchema}string",
                List.of(),
                null,
                5,
                null,
                null,
                null,
                null);

        new RestrictionMapper(ExpressionFactory.eINSTANCE, runeFactory).map(alias, node, true);

        RosettaIntLiteral maximum = assertInstanceOf(
                RosettaIntLiteral.class,
                typeCall.getArguments().get(0).getValue());
        assertEquals(BigInteger.ZERO, maximum.getValue());
    }

    private static void addParameter(
            RosettaTypeAlias type,
            String name,
            RosettaFactory runeFactory) {
        TypeParameter parameter = runeFactory.createTypeParameter();
        parameter.setName(name);
        type.getParameters().add(parameter);
    }
}
