package lk.swiftdrop.merchant.credential;

/**
 * An OAuth client whose secret was just created or replaced, together with the raw secret, which exists only
 * here and is shown to the merchant once. {@link #toString()} leaves the secret out so that it cannot end up
 * in a log.
 */
public record IssuedOAuthClient(OAuthClient client, String rawSecret) {

    @Override
    public String toString() {
        return "IssuedOAuthClient[clientId=" + client.getClientId() + "]";
    }
}
