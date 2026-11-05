package lk.swiftdrop.identity.controller;

import lk.swiftdrop.identity.domain.Role;
import lk.swiftdrop.identity.domain.User;
import lk.swiftdrop.identity.dto.LoginRequest;
import lk.swiftdrop.identity.repository.UserRepository;
import lk.swiftdrop.identity.security.PasswordService;
import lk.swiftdrop.identity.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginEndpointTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @BeforeEach
    void setUpUser() {
        String hash = passwordService.hash("ValidPassword123!");
        User user = User.register("Saman Silva", "saman@example.com", "+94779876543", hash, Role.MERCHANT, Instant.now());
        userRepository.save(user);
    }

    @Test
    void successfulLoginReturnsSession() throws Exception {
        LoginRequest request = new LoginRequest("saman@example.com", "ValidPassword123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("saman@example.com"))
                .andExpect(jsonPath("$.user.role").value("MERCHANT"));
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        LoginRequest request = new LoginRequest("saman@example.com", "WrongPassword!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void unknownEmailReturns401WithSameMessage() throws Exception {
        LoginRequest request = new LoginRequest("unknown@example.com", "AnyPassword!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void fiveConsecutiveFailedLoginsTriggersAccountLockout() throws Exception {
        LoginRequest request = new LoginRequest("saman@example.com", "WrongPassword!");

        // First 4 failures -> 401
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(request)))
                    .andExpect(status().isUnauthorized());
        }

        // 5th failure -> 429 Too Many Requests (Lockout activated)
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(header().exists("Retry-After"));

        // Subsequent attempt with right password while locked -> still 429
        LoginRequest correct = new LoginRequest("saman@example.com", "ValidPassword123!");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(correct)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }
}
