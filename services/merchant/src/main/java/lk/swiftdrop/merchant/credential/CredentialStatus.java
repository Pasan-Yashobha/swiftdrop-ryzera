package lk.swiftdrop.merchant.credential;

/** Lifecycle of an API key or OAuth client; a revoked credential never works again. */
public enum CredentialStatus {
    ACTIVE, REVOKED
}
