package org.finos.rune.importer.xsd.mapper.mapping;

import org.finos.rune.importer.xsd.registry.RegistryKey;

import java.util.Objects;

/**
 * Registration-time binding from one global XSD element to its Rune model target.
 *
 * <p>The source element is the key under which this binding is stored. The target
 * key identifies the Rune model target: it is the element key for wrappers and
 * inline content, and the referenced type key for proxy and merge modes.</p>
 */
public record GlobalElementBinding(Mode mode, RegistryKey targetKey) {

    public GlobalElementBinding {
        Objects.requireNonNull(mode, "global element binding mode must not be null");
        Objects.requireNonNull(targetKey, "global element binding target key must not be null");
    }

    public enum Mode {
        VIRTUAL_ALIAS,
        NATIVE_WRAPPER,
        INLINE_PROXY,
        MERGED_TYPED_ELEMENT,
        TYPED_PROXY,
        ANONYMOUS_INLINE
    }
}
