package org.finos.rune.importer.xsd.security;

/** Raised when an XSD violates the active intake security policy. */
public class XsdSecurityException extends RuntimeException {

    public XsdSecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}
