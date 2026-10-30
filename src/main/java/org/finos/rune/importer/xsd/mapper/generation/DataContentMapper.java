package org.finos.rune.importer.xsd.mapper.generation;

import com.regnosys.rosetta.rosetta.simple.Attribute;
import com.regnosys.rosetta.rosetta.simple.Data;
import org.finos.rune.importer.xsd.inspector.model.InspectorAttributeNode;
import org.finos.rune.importer.xsd.inspector.model.InspectorChoiceNode;
import org.finos.rune.importer.xsd.inspector.model.InspectorGroupNode;
import org.finos.rune.importer.xsd.inspector.model.InspectorNode;
import org.finos.rune.importer.xsd.mapper.context.MappingContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maps the inspector's neutral content model into target Rune attributes and
 * conditions.
 */
public final class DataContentMapper {

    private final DataAttributeMapper attributeMapper;

    public DataContentMapper(DataAttributeMapper attributeMapper) {
        this.attributeMapper = attributeMapper;
    }

    /**
     * Core logic to translate InspectorNodes into Rune Attributes and Conditions.
     */
    public void map(Data data, List<InspectorNode> nodes, MappingContext context) {
        AtomicInteger choiceCounter = new AtomicInteger(1);

        for (InspectorNode node : nodes) {
            if (node instanceof InspectorAttributeNode attrNode) {
                attributeMapper.map(
                        data,
                        attrNode.name(),
                        attrNode.typeQName(),
                        attrNode.min(),
                        attrNode.max(),
                        attrNode.documentation(),
                        context,
                        attrNode.elementRefUri());
            } else if (node instanceof InspectorGroupNode groupNode) {
                attributeMapper.map(
                        data,
                        groupNode.groupName(),
                        groupNode.groupTypeQName(),
                        groupNode.min(),
                        groupNode.max(),
                        groupNode.documentation(),
                        context,
                        Optional.empty());
            } else if (node instanceof InspectorChoiceNode choiceNode) {
                processChoiceBlock(data, choiceNode, choiceCounter, true, false, context);
            }
        }
    }

    /**
     * Recursive method to handle Nested Choices via Option 2 (Flattening).
     * * @param createCondition If true, generates a Condition for this block.
     * If false (nested), bubbles up attribute names to the parent.
     * @return List of all attribute names created within this block (including children).
     */
    private List<String> processChoiceBlock(Data data, InspectorChoiceNode choiceNode, AtomicInteger indexCounter, boolean createCondition, boolean forceRepeating, MappingContext context) {
        List<String> collectedAttributes = new ArrayList<>();
        List<InspectorNode> options = choiceNode.options();

        // A repeated XSD choice can select different branches at different occurrences.
        // The limitation is documented as a known gap; do not treat the generated cardinalities
        // as a complete repeated-choice model.
        boolean isRepeating = forceRepeating || choiceNode.max() > 1;

        for (InspectorNode option : options) {
            String attrName = null;

            if (option instanceof InspectorAttributeNode attrNode) {
                int max = isRepeating ? choiceNode.max() : attrNode.max();
                Attribute attr = attributeMapper.map(data, attrNode.name(), attrNode.typeQName(), attrNode.min(), max, attrNode.documentation(), context, attrNode.elementRefUri());
                if (attr != null) attrName = attr.getName();
            }
            else if (option instanceof InspectorGroupNode groupNode) {
                int max = isRepeating ? choiceNode.max() : groupNode.max();
                Attribute attr = attributeMapper.map(data, groupNode.groupName(), groupNode.groupTypeQName(), groupNode.min(), max, groupNode.documentation(), context, java.util.Optional.empty());
                if (attr != null) attrName = attr.getName();
            }
            else if (option instanceof InspectorChoiceNode innerChoiceNode) {
                // Nested choices are flattened into the parent condition.
                List<String> innerAttrs = processChoiceBlock(data, innerChoiceNode, indexCounter, false, isRepeating, context);
                collectedAttributes.addAll(innerAttrs);
            }

            if (attrName != null) {
                collectedAttributes.add(attrName);
            }
        }

        if (createCondition && !collectedAttributes.isEmpty()) {
            // An XSD xs:choice minOccurs maps to required/optional choice; it is not the same source construct as a mixed xs:union.
            context.conditionMapper().addChoiceCondition(
                    data, collectedAttributes, indexCounter.getAndIncrement(), choiceNode.min() > 0, false);
        }

        return collectedAttributes;
    }
}
