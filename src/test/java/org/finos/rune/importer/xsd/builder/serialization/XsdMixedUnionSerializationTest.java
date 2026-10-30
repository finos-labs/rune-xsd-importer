package org.finos.rune.importer.xsd.builder.serialization;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.rosetta.model.lib.RosettaModelObject;
import com.rosetta.model.lib.RosettaModelObjectBuilder;
import com.rosetta.model.lib.meta.RosettaMetaData;
import com.rosetta.model.lib.path.RosettaPath;
import com.rosetta.model.lib.process.Processor;
import com.rosetta.model.lib.records.Date;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class XsdMixedUnionSerializationTest {

    @Test
    void serializerUsesTheModelProcessingOrderRatherThanGetterReflectionOrder() throws Exception {
        StringWriter output = new StringWriter();
        JsonGenerator generator = new JsonFactory().createGenerator(output);

        new XsdMixedUnionSerializer(RosettaModelObject.class)
                .serialize(new OrderedUnion("first", "second"), generator, null);
        generator.close();

        assertEquals("\"first\"", output.toString());
    }

    @Test
    void deserializerOrdersCandidateSettersByNameAndParameterType() {
        List<Method> setters = XsdMixedUnionDeserializer.orderedSetters(UnorderedBuilder.class);

        assertEquals(List.of("setAlpha", "setBeta", "setBeta"),
                setters.stream().map(Method::getName).toList());
        assertEquals(List.of(Integer.class, Long.class, String.class),
                setters.stream().map(method -> method.getParameterTypes()[0]).toList());
    }

    @Test
    void dateCoercionDoesNotTreatBroadSupertypesAsDateSlots() throws Exception {
        assertNull(XsdMixedUnionDeserializer.coerceToType("2026-02-26", Comparable.class));
        assertEquals(Date.of(LocalDate.of(2026, 2, 26)),
                XsdMixedUnionDeserializer.coerceToType("2026-02-26", Date.class));
    }

    static final class UnorderedBuilder {
        public void setBeta(Long value) {
        }

        public void setAlpha(Integer value) {
        }

        public void setBeta(String value) {
        }
    }

    private static final class OrderedUnion implements RosettaModelObject {
        private final String first;
        private final String second;

        private OrderedUnion(String first, String second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public RosettaModelObjectBuilder toBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public RosettaModelObject build() {
            return this;
        }

        @Override
        public RosettaMetaData<? extends RosettaModelObject> metaData() {
            return null;
        }

        @Override
        public Class<? extends RosettaModelObject> getType() {
            return OrderedUnion.class;
        }

        @Override
        public void process(RosettaPath path, Processor processor) {
            processor.processBasic(path.newSubPath("first"), String.class, first, this);
            processor.processBasic(path.newSubPath("second"), String.class, second, this);
        }
    }
}
