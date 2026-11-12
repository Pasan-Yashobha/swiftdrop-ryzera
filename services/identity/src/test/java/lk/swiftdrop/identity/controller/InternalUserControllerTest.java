package lk.swiftdrop.identity.controller;

import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.identity.domain.Role;
import lk.swiftdrop.identity.domain.User;
import lk.swiftdrop.identity.repository.UserRepository;
import lk.swiftdrop.identity.security.InternalTokenFilter;
import lk.swiftdrop.identity.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalUserControllerTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    private static final String VALID_INTERNAL_TOKEN = "test-internal-token";

    @Test
    void getUser_withValidInternalToken_returns200AndUser() throws Exception {
        User user = userRepository.save(User.register(
                "Chaminda Vaas",
                "vaas@example.com",
                "+94771122334",
                "hash",
                Role.COURIER,
                Instant.now()
        ));

        mockMvc.perform(get("/internal/users/" + user.getId())
                        .header(InternalTokenFilter.HEADER, VALID_INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("vaas@example.com"))
                .andExpect(jsonPath("$.fullName").value("Chaminda Vaas"))
                .andExpect(jsonPath("$.role").value("COURIER"));
    }

    @Test
    void getUser_withoutInternalToken_returns401Unauthorized() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(get("/internal/users/" + randomId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUser_withInvalidInternalToken_returns401Unauthorized() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(get("/internal/users/" + randomId)
                        .header(InternalTokenFilter.HEADER, "wrong-internal-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUser_whenNotFound_returns404NotFound() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(get("/internal/users/" + randomId)
                        .header(InternalTokenFilter.HEADER, VALID_INTERNAL_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}

