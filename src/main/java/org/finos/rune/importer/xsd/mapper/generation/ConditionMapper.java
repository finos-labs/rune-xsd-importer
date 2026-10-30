package org.finos.rune.importer.xsd.mapper.generation;

import com.regnosys.rosetta.rosetta.expression.ChoiceOperation;
import com.regnosys.rosetta.rosetta.expression.ExpressionFactory;
import com.regnosys.rosetta.rosetta.expression.Necessity;
import com.regnosys.rosetta.rosetta.expression.OneOfOperation;
import com.regnosys.rosetta.rosetta.simple.Attribute;
import com.regnosys.rosetta.rosetta.simple.Condition;
import com.regnosys.rosetta.rosetta.simple.Data;
import com.regnosys.rosetta.rosetta.simple.SimpleFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Maps the existing flattened choice and mixed-union conditions to Rune
 * condition symbols on Data.
 */
public final class ConditionMapper {

    private static final Logger log = LoggerFactory.getLogger(ConditionMapper.class);

    private final SimpleFactory simpleFactory = SimpleFactory.eINSTANCE;
    private final ExpressionFactory expressionFactory = ExpressionFactory.eINSTANCE;

    public void addChoiceCondition(Data data, List<String> attributeNames, int index, boolean isChoiceRequired, boolean isMixedUnion) {
        Condition cond = simpleFactory.createCondition();
        cond.setName("Choice" + index);

        List<Attribute> choiceAttrs = data.getAttributes().stream()
                .filter(a -> attributeNames.contains(a.getName()))
                .toList();

        if (attributeNames.size() > 1) {
            if (isMixedUnion) {
                // Mixed union members are optional value slots; one-of applies
                // to the complete generated union type.
                log.trace("Generating OneOfOperation for mixed union '{}' with {} attributes", data.getName(), choiceAttrs.size());
                OneOfOperation oneOfOperation = expressionFactory.createOneOfOperation();
                oneOfOperation.setOperator("one-of");
                cond.setExpression(oneOfOperation);
            } else {
                // An ordinary choice preserves whether the source choice is required.
                log.trace("Generating ChoiceOperation for '{}' with {} attributes. Required: {}", data.getName(), choiceAttrs.size(), isChoiceRequired);
                ChoiceOperation choiceOperation = expressionFactory.createChoiceOperation();
                choiceOperation.setOperator("choice");
                choiceOperation.setNecessity(isChoiceRequired ? Necessity.REQUIRED : Necessity.OPTIONAL);
                choiceOperation.getAttributes().addAll(choiceAttrs);
                cond.setExpression(choiceOperation);
            }

            data.getConditions().add(cond);
            // Multi-branch choice branches are optional individually; the condition carries
            // the choice requirement. A single-branch choice keeps its mapped lower bound.
            choiceAttrs.forEach(attr -> {
                if (attr.getCard().getInf() > 0) {
                    log.trace("Relaxing cardinality for '{}' from {} to 0 because of Choice context.", attr.getName(), attr.getCard().getInf());
                    attr.getCard().setInf(0);
                }
            });
        }
    }
}
