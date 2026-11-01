package lk.swiftdrop.merchant.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lk.swiftdrop.merchant.testsupport.AbstractIntegrationTest;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.Test;

class InternalEndpointSecurityTest extends AbstractIntegrationTest {

    @Test
    void internalPathsWithoutTokenAreUnauthenticated() throws Exception {
        mockMvc.perform(get("/internal/merchants/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void internalPathsWithWrongTokenAreUnauthenticated() throws Exception {
        mockMvc.perform(get("/internal/merchants/1").header(InternalTokenFilter.HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalTokenIsAcceptedAndNothingElseIsNeeded() throws Exception {
        // No controller is mapped here (the webhook module adds them): getting past security means a plain 404.
        mockMvc.perform(get("/internal/merchants/1").with(TestAuth.internal()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void bearerTokensAreNotConsideredOnInternalPaths() throws Exception {
        mockMvc.perform(get("/internal/merchants/1").header("Authorization", "Bearer some.identity.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void authenticatedUsersAreNotInternalServices() throws Exception {
        mockMvc.perform(get("/internal/merchants/1").with(TestAuth.admin("admin-1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalTokenDoesNotGrantAccessToUserEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/me").with(TestAuth.internal()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/merchants").with(TestAuth.internal()))
                .andExpect(status().isUnauthorized());
    }
}
