package org.finos.rune.importer.xsd.mapper.generation;

import com.regnosys.rosetta.rosetta.*;
import com.regnosys.rosetta.rosetta.expression.*;
import org.finos.rune.importer.xsd.inspector.model.InspectorSimpleTypeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Maps supported XSD restriction facets to Rune type-call arguments.
 *
 * <p>The facet table is deliberately explicit: adding a supported facet
 * requires one rule with a source value and literal policy rather than another
 * branch in the alias mapper.</p>
 */
public final class RestrictionMapper {

    private static final Logger log = LoggerFactory.getLogger(RestrictionMapper.class);

    private final ExpressionFactory expressionFactory;
    private final RosettaFactory runeFactory;
    private final List<FacetRule> facetRules;

    public RestrictionMapper(ExpressionFactory expressionFactory, RosettaFactory runeFactory) {
        this.expressionFactory = expressionFactory;
        this.runeFactory = runeFactory;
        this.facetRules = List.of(
                new FacetRule("pattern", RestrictionMapper::firstPattern, LiteralKind.STRING),
                new FacetRule("minLength", node -> integerValue(node.minLength()), LiteralKind.INTEGER),
                new FacetRule("maxLength", node -> integerValue(node.maxLength()), LiteralKind.INTEGER),
                new FacetRule("min", InspectorSimpleTypeNode::minInclusive, LiteralKind.NUMERIC),
                new FacetRule("max", InspectorSimpleTypeNode::maxInclusive, LiteralKind.NUMERIC),
                new FacetRule("digits", node -> integerValue(node.totalDigits()), LiteralKind.INTEGER),
                new FacetRule("fractionalDigits", node -> integerValue(node.fractionDigits()), LiteralKind.INTEGER)
        );
    }

    public void map(
            RosettaTypeAlias alias,
            InspectorSimpleTypeNode node,
            boolean emptyStringEnumeration) {
        TypeCall typeCall = alias.getTypeCall();
        if (typeCall == null) {
            return;
        }

        RosettaType baseType = typeCall.getType();
        if (baseType == null) {
            log.warn("Cannot map restrictions: Base type is null for alias '{}'", alias.getName());
            return;
        }

        for (FacetRule rule : facetRules) {
            String lexicalValue = emptyStringEnumeration && "maxLength".equals(rule.parameterName())
                    ? "0"
                    : rule.valueExtractor().apply(node);
            if (lexicalValue != null) {
                mapFacet(alias, baseType, typeCall, rule, lexicalValue);
            }
        }
    }

    private void mapFacet(
            RosettaTypeAlias alias,
            RosettaType baseType,
            TypeCall typeCall,
            FacetRule rule,
            String lexicalValue) {
        findParameter(baseType, rule.parameterName()).ifPresentOrElse(parameter -> {
            RosettaLiteral literal = createLiteral(rule.literalKind(), lexicalValue, alias, rule.parameterName());
            if (literal == null) {
                return;
            }

            TypeCallArgument argument = runeFactory.createTypeCallArgument();
            argument.setParameter(parameter);
            argument.setValue(literal);
            typeCall.getArguments().add(argument);
            log.trace("Mapped {} restriction for '{}'", rule.parameterName(), alias.getName());
        }, () -> log.trace("Parameter '{}' not applicable/found on base type '{}'",
                rule.parameterName(), baseType.getName()));
    }

    /**
     * Numeric XSD lexical values are mapped by their lexical shape: integral
     * values use Rune integer literals, while decimal or exponent values use
     * Rune number literals. Invalid values are skipped with a diagnostic.
     */
    private RosettaLiteral createLiteral(
            LiteralKind kind,
            String lexicalValue,
            RosettaTypeAlias alias,
            String facetName) {
        try {
            return switch (kind) {
                case STRING -> {
                    RosettaStringLiteral literal = expressionFactory.createRosettaStringLiteral();
                    literal.setValue(lexicalValue);
                    yield literal;
                }
                case INTEGER -> {
                    RosettaIntLiteral literal = expressionFactory.createRosettaIntLiteral();
                    literal.setValue(new BigInteger(lexicalValue));
                    yield literal;
                }
                case NUMERIC -> createNumericLiteral(lexicalValue);
            };
        } catch (NumberFormatException exception) {
            log.warn("Invalid {} value for alias '{}'", facetName, alias.getName(), exception);
            return null;
        }
    }

    private RosettaLiteral createNumericLiteral(String lexicalValue) {
        if (lexicalValue.matches("[+-]?\\d+")) {
            RosettaIntLiteral literal = expressionFactory.createRosettaIntLiteral();
            literal.setValue(new BigInteger(lexicalValue));
            return literal;
        }

        RosettaNumberLiteral literal = expressionFactory.createRosettaNumberLiteral();
        literal.setValue(new BigDecimal(lexicalValue));
        return literal;
    }

    /**
     * Helper to find a TypeParameter by name on a RosettaType.
     * Useful for looking up parameters on bootstrapped primitives (e.g. finding 'pattern' on 'string').
     */
    private Optional<TypeParameter> findParameter(RosettaType type, String name) {
        if (type instanceof ParametrizedRosettaType alias) {
            return alias.getParameters().stream()
                    .filter(parameter -> parameter.getName().equals(name))
                    .findFirst();
        }
        log.trace("findParameter: Type '{}' is not an alias (Class: {}), cannot have parameters.",
                type.getName(), type.getClass().getSimpleName());
        return Optional.empty();
    }

    private static String firstPattern(InspectorSimpleTypeNode node) {
        return node.patterns() == null || node.patterns().isEmpty()
                ? null
                : node.patterns().get(0);
    }

    private static String integerValue(Integer value) {
        return value == null ? null : value.toString();
    }

    private record FacetRule(
            String parameterName,
            Function<InspectorSimpleTypeNode, String> valueExtractor,
            LiteralKind literalKind) {
    }

    private enum LiteralKind {
        STRING,
        INTEGER,
        NUMERIC
    }
}
