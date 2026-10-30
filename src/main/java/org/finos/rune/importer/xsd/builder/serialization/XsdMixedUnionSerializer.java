package org.finos.rune.importer.xsd.builder.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.rosetta.model.lib.RosettaModelObject;
import com.rosetta.model.lib.path.RosettaPath;
import com.rosetta.model.lib.process.AttributeMeta;
import com.rosetta.model.lib.process.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;

public class XsdMixedUnionSerializer extends StdSerializer<RosettaModelObject> {

    private static final Logger log = LoggerFactory.getLogger(XsdMixedUnionSerializer.class);

    @SuppressWarnings("unchecked")
    public XsdMixedUnionSerializer(Class<? extends RosettaModelObject> t) {
        super((Class<RosettaModelObject>) t);
        log.debug("Initialized XsdMixedUnionSerializer for target class: {}", t.getSimpleName());
    }

    @Override
    public void serialize(RosettaModelObject value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        MixedUnionValueProcessor processor = new MixedUnionValueProcessor();
        value.process(RosettaPath.valueOf("mixedUnion"), processor);
        Object memberValue = processor.value();
        if (memberValue == null) {
            log.trace("All mixed-union members were null. Writing empty string.");
            gen.writeString("");
            return;
        }
        // Flat XSD union values are represented by their lexical text.
        gen.writeString(memberValue.toString());
    }

    @Override
    public void serializeWithType(RosettaModelObject value, JsonGenerator gen, SerializerProvider serializers, TypeSerializer typeSer) throws IOException {
        log.trace("Bypassing TypeSerializer for Mixed Union '{}' (XSD Unions do not support xsi:type)", value.getClass().getSimpleName());
        serialize(value, gen, serializers);
    }

    private static final class MixedUnionValueProcessor implements Processor {
        private Object value;

        @Override
        public <R extends RosettaModelObject> boolean processRosetta(
                RosettaPath path,
                Class<? extends R> rosettaType,
                R memberValue,
                RosettaModelObject parent,
                AttributeMeta... metas) {
            capture(memberValue);
            return false;
        }

        @Override
        public <R extends RosettaModelObject> boolean processRosetta(
                RosettaPath path,
                Class<? extends R> rosettaType,
                java.util.List<? extends R> memberValues,
                RosettaModelObject parent,
                AttributeMeta... metas) {
            capture(firstNonNull(memberValues));
            return false;
        }

        @Override
        public <T> void processBasic(
                RosettaPath path,
                Class<? extends T> basicType,
                T memberValue,
                RosettaModelObject parent,
                AttributeMeta... metas) {
            capture(memberValue);
        }

        @Override
        public <T> void processBasic(
                RosettaPath path,
                Class<? extends T> basicType,
                Collection<? extends T> memberValues,
                RosettaModelObject parent,
                AttributeMeta... metas) {
            capture(firstNonNull(memberValues));
        }

        @Override
        public Report report() {
            return null;
        }

        private Object value() {
            return value;
        }

        private void capture(Object candidate) {
            if (value == null && candidate != null) {
                value = candidate;
            }
        }

        private Object firstNonNull(Collection<?> values) {
            if (values == null) {
                return null;
            }
            return values.stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
        }
    }
}
