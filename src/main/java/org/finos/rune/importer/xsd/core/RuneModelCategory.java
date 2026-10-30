package org.finos.rune.importer.xsd.core;

import java.util.Locale;

public enum RuneModelCategory {
    TYPE,
    ENUM;

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
