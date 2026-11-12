package lk.swiftdrop.identity.controller;

import lk.swiftdrop.identity.dto.SignupRequest;
import lk.swiftdrop.identity.repository.UserRepository;
import lk.swiftdrop.identity.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SignupEndpointTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void successfulSignupCreatesUserAndSession() throws Exception {
        SignupRequest request = new SignupRequest(
                "Kamal Perera",
                "kamal@example.com",
                "+94771234567",
                "Password123!",
                "CUSTOMER"
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("kamal@example.com"))
                .andExpect(jsonPath("$.user.fullName").value("Kamal Perera"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(header().exists("Set-Cookie"));

        assertThat(userRepository.existsByEmailAndDeletedAtIsNull("kamal@example.com")).isTrue();
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        SignupRequest request1 = new SignupRequest("Kamal Perera", "kamal@example.com", null, "Password123!", "CUSTOMER");
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json(request1)))
                .andExpect(status().isCreated());

        SignupRequest request2 = new SignupRequest("Another User", "kamal@example.com", null, "Password123!", "CUSTOMER");
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        SignupRequest request = new SignupRequest("", "not-an-email", "invalid-phone", "short", null);
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
