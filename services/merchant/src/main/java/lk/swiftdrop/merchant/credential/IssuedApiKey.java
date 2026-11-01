package lk.swiftdrop.merchant.credential;

/**
 * A freshly created or rotated API key together with its raw value, which exists only here and is shown to
 * the merchant once. {@link #toString()} leaves the raw key out so that it cannot end up in a log.
 */
public record IssuedApiKey(ApiKey key, String rawKey) {

    @Override
    public String toString() {
        return "IssuedApiKey[prefix=" + key.getKeyPrefix() + "]";
    }
}
