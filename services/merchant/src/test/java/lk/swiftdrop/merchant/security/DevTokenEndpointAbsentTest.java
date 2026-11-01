package lk.swiftdrop.merchant.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lk.swiftdrop.merchant.testsupport.AbstractIntegrationTest;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;

/** Outside the dev profile the token endpoint must not exist at all. */
class DevTokenEndpointAbsentTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void controllerBeanIsNotCreated() {
        assertThat(context.getBeanNamesForType(DevTokenController.class)).isEmpty();
    }

    @Test
    void endpointIsNotReachable() throws Exception {
        String body = "{\"sub\":\"admin-1\",\"role\":\"ADMIN\"}";

        mockMvc.perform(post("/api/v1/dev/tokens").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/dev/tokens").with(TestAuth.admin("admin-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }
}
