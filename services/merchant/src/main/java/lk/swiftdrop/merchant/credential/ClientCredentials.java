package lk.swiftdrop.merchant.credential;

/**
 * Client id and secret presented to the token endpoint. {@link #toString()} leaves the secret out.
 *
 * @param viaBasic whether they came in an HTTP Basic header (an authentication failure then asks for Basic)
 */
public record ClientCredentials(String clientId, String clientSecret, boolean viaBasic) {

    @Override
    public String toString() {
        return "ClientCredentials[viaBasic=" + viaBasic + "]";
    }
}
