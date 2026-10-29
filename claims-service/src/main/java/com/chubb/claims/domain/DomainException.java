package com.chubb.claims.domain;

/** Business-rule violation. The API layer maps {@link Kind} to an HTTP status. */
public class DomainException extends RuntimeException {

    public enum Kind {
        /** Request is well-formed but conflicts with the claim's current state → 409 */
        CONFLICT,
        /** Actor may not perform this action on this claim → 403 */
        FORBIDDEN,
        /** Input breaks a business rule (e.g. negative reserve) → 400 */
        INVALID
    }

    private final Kind kind;

    public DomainException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public static DomainException conflict(String message) {
        return new DomainException(Kind.CONFLICT, message);
    }

    public static DomainException forbidden(String message) {
        return new DomainException(Kind.FORBIDDEN, message);
    }

    public static DomainException invalid(String message) {
        return new DomainException(Kind.INVALID, message);
    }
}
