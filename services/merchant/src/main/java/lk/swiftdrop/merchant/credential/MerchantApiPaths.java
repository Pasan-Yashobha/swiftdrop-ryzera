package lk.swiftdrop.merchant.credential;

/** URL paths of the merchant API chain. */
public final class MerchantApiPaths {

    /** Everything below this path is the merchant API and needs an API key or access token. */
    public static final String BASE = "/api/v1/merchant-api";
    public static final String PING = BASE + "/ping";
    public static final String OPENAPI = BASE + "/openapi.yaml";
    public static final String TOKEN = "/api/v1/oauth/token";

    private MerchantApiPaths() {
    }

    /** Paths that need no credential: the OpenAPI document and the token endpoint itself. */
    static boolean isPublic(String path) {
        return OPENAPI.equals(path) || TOKEN.equals(path);
    }
}
