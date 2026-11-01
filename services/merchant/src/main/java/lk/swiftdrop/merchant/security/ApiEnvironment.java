package lk.swiftdrop.merchant.security;

/** Environment of a merchant API credential: TEST credentials work in sandbox mode only. */
public enum ApiEnvironment {
    LIVE, TEST;

    public boolean isSandbox() {
        return this == TEST;
    }
}
