package lk.swiftdrop.identity.security;

import lk.swiftdrop.identity.dto.GoogleUserInfo;

/**
 * Client for exchanging OAuth 2.0 authorization codes with Google and extracting verified user claims.
 */
public interface GoogleOAuthClient {

    /**
     * Exchanges the given authorization code with Google token endpoint and returns verified user info.
     *
     * @param code the authorization code received from Google callback
     * @return verified Google user information
     */
    GoogleUserInfo exchangeCode(String code);
}

