package lk.swiftdrop.identity.controller;

import lk.swiftdrop.identity.domain.Role;
import lk.swiftdrop.identity.domain.User;
import lk.swiftdrop.identity.dto.ChangePasswordRequest;
import lk.swiftdrop.identity.dto.UpdateProfileRequest;
import lk.swiftdrop.identity.repository.UserRepository;
import lk.swiftdrop.identity.security.JwtService;
import lk.swiftdrop.identity.security.PasswordService;
import lk.swiftdrop.identity.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserProfileEndpointTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private JwtService jwtService;

    private User user;
    private String token;
    private UUID familyId;

    @BeforeEach
    void setUp() {
        String hash = passwordService.hash("OldPassword123!");
        this.user = userRepository.save(User.register("Anura Bandara", "anura@example.com", "+94771122334", hash, Role.CUSTOMER, Instant.now()));
        this.familyId = UUID.randomUUID();
        this.token = jwtService.generateAccessToken(user, familyId);
    }

    @Test
    void unauthenticatedCallIsDenied() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getsOwnProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("anura@example.com"))
                .andExpect(jsonPath("$.fullName").value("Anura Bandara"));
    }

    @Test
    void updatesProfile() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("Anura B. Perera", "+94779988776", "https://img.example.com/avatar.png");

        mockMvc.perform(put("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Anura B. Perera"))
                .andExpect(jsonPath("$.phone").value("+94779988776"))
                .andExpect(jsonPath("$.avatarUrl").value("https://img.example.com/avatar.png"));
    }

    @Test
    void changesPasswordSuccessfully() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword123!", "NewPassword123!");

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk());

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordService.matches("NewPassword123!", updatedUser.getPasswordHash())).isTrue();
    }

    @Test
    void passwordChangeRejectsWrongCurrentPassword() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("WrongCurrentPw!", "NewPassword123!");

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
}
