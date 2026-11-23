package org.finos.rune.importer.xsd.builder.serialization;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.util.NameTransformer;
import com.fasterxml.jackson.dataformat.xml.deser.WrapperHandlingDeserializer;
import com.fasterxml.jackson.dataformat.xml.util.TypeUtil;
import com.rosetta.model.lib.ModelSymbolId;
import com.rosetta.util.serialisation.RosettaXMLConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Prepares repeated elements of virtual groups on the owning XML parser before
 * Jackson 2.x buffers the group. The buffered parser cannot perform XML list wrapping.
 *
 * <p>Remove this adapter and its registration once the Rune XML runtime uses
 * Jackson 3.2 or later and the repeated-group regression and full verification
 * pass without it. Evaluate the virtual-property type-resolution guard in
 * {@link XsdPolymorphicIntrospector} separately.</p>
 */
final class XsdVirtualGroupDeserializerModifier extends BeanDeserializerModifier {

    private final RosettaXMLConfiguration configuration;

    XsdVirtualGroupDeserializerModifier(RosettaXMLConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config, BeanDescription description,
                                                  JsonDeserializer<?> deserializer) {
        if (!(deserializer instanceof BeanDeserializerBase bean)
                || configuration.getConfigurationForType(modelId(description.getBeanClass())).isEmpty()) {
            return deserializer;
        }

        // Jackson removes unwrapped properties from the bean during resolution.
        List<SettableBeanProperty> groups = new ArrayList<>();
        boolean hasUnwrappedList = false;
        var properties = bean.properties();
        while (properties.hasNext()) {
            SettableBeanProperty property = properties.next();
            if (XsdPolymorphicIntrospector.isVirtualProperty(configuration, property.getMember())) {
                groups.add(property);
            }
            hasUnwrappedList |= isUnwrappedList(property);
        }
        return groups.isEmpty() && !hasUnwrappedList ? deserializer : new GroupWrappingDeserializer(bean, groups);
    }

    private static ModelSymbolId modelId(Class<?> type) {
        return ModelSymbolId.fromQualifiedName(XsdUnionIntrospector.modelQualifiedName(type));
    }

    private static boolean isUnwrappedList(SettableBeanProperty property) {
        PropertyName wrapperName = property.getWrapperName();
        return TypeUtil.isIndexedType(property.getType())
                && (wrapperName == null || wrapperName == PropertyName.NO_NAME);
    }

    private final class GroupWrappingDeserializer extends WrapperHandlingDeserializer {

        private final List<SettableBeanProperty> groups;

        private GroupWrappingDeserializer(BeanDeserializerBase bean, List<SettableBeanProperty> groups) {
            super(bean);
            this.groups = List.copyOf(groups);
        }

        @Override
        public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property)
                throws JsonMappingException {
            JsonDeserializer<?> contextual = super.createContextual(context, property);
            BeanDeserializerBase bean = contextual instanceof WrapperHandlingDeserializer wrapper
                    ? (BeanDeserializerBase) wrapper.getDelegatee()
                    : (BeanDeserializerBase) contextual;
            if (property != null && XsdPolymorphicIntrospector.isVirtualProperty(configuration, property.getMember())) {
                // Jackson will unwrap this bean directly when binding the virtual property.
                return bean;
            }

            DeserializationConfig config = context.getConfig();
            Set<String> groupLists = new HashSet<>();
            for (SettableBeanProperty group : groups) {
                JsonDeserializer<?> groupDeserializer = context.findNonContextualValueDeserializer(group.getType());
                if (groupDeserializer instanceof GroupWrappingDeserializer groupWrapper) {
                    NameTransformer transformer = config.getAnnotationIntrospector()
                            .findUnwrappingNameTransformer(group.getMember());
                    if (transformer != null) {
                        addListNames(groupLists, (BeanDeserializerBase) groupWrapper.getDelegatee(),
                                config, transformer);
                    }
                }
            }
            if (groupLists.isEmpty()) {
                return contextual;
            }
            addListNames(groupLists, bean, config, NameTransformer.NOP);
            return new WrapperHandlingDeserializer(bean, Set.copyOf(groupLists));
        }

        private void addListNames(Set<String> names, BeanDeserializerBase bean, DeserializationConfig config,
                                  NameTransformer transformer) {
            bean.properties().forEachRemaining(property -> {
                if (isUnwrappedList(property)) {
                    names.add(transformer.transform(property.getName()));
                    property.findAliases(config).forEach(alias -> names.add(transformer.transform(alias.getSimpleName())));
                }
            });
        }
    }
}
