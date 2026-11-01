package lk.swiftdrop.merchant.credential;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import lk.swiftdrop.merchant.event.OutboxRepository;
import lk.swiftdrop.merchant.merchant.MerchantService;
import lk.swiftdrop.merchant.testsupport.AbstractIntegrationTest;
import lk.swiftdrop.merchant.testsupport.CredentialFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Integration test with a clock the test controls and the credential fixtures at hand. */
@Import(ControllableClock.Config.class)
abstract class AbstractCredentialTest extends AbstractIntegrationTest {

    @Autowired
    protected ControllableClock clock;

    @Autowired
    protected CredentialFixtures credentials;

    @Autowired
    protected ApiKeyRepository apiKeys;

    @Autowired
    protected OAuthClientRepository oauthClients;

    @Autowired
    protected OutboxRepository outbox;

    @Autowired
    protected MerchantService merchantService;

    @Autowired
    protected ApiKeyService apiKeyService;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    protected static String basic(String clientId, String clientSecret) {
        return "Basic " + HttpHeaders.encodeBasicAuth(clientId, clientSecret, StandardCharsets.UTF_8);
    }

    /** A token request with client_credentials grant; the caller adds the client authentication. */
    protected static MockHttpServletRequestBuilder tokenRequest() {
        return post("/api/v1/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials");
    }
}
