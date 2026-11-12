package lk.swiftdrop.identity.controller;

import lk.swiftdrop.identity.domain.Role;
import lk.swiftdrop.identity.domain.User;
import lk.swiftdrop.identity.dto.ChangeRoleRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserEndpointTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private JwtService jwtService;

    private User admin1;
    private User admin2;
    private User customer;
    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        String hash = passwordService.hash("Pass12345!");
        this.admin1 = userRepository.save(User.register("Admin One", "admin1@example.com", null, hash, Role.ADMIN, Instant.now()));
        this.admin2 = userRepository.save(User.register("Admin Two", "admin2@example.com", null, hash, Role.ADMIN, Instant.now()));
        this.customer = userRepository.save(User.register("Customer", "customer@example.com", null, hash, Role.CUSTOMER, Instant.now()));

        this.adminToken = jwtService.generateAccessToken(admin1, UUID.randomUUID());
        this.customerToken = jwtService.generateAccessToken(customer, UUID.randomUUID());
    }

    @Test
    void nonAdminIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void adminCanChangeUserRole() throws Exception {
        ChangeRoleRequest request = new ChangeRoleRequest(Role.COURIER);

        mockMvc.perform(put("/api/v1/admin/users/" + customer.getId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COURIER"));

        User updated = userRepository.findById(customer.getId()).orElseThrow();
        assertThat(updated.getRole()).isEqualTo(Role.COURIER);
    }

    @Test
    void adminCannotDemoteSelf() throws Exception {
        ChangeRoleRequest request = new ChangeRoleRequest(Role.CUSTOMER);

        mockMvc.perform(put("/api/v1/admin/users/" + admin1.getId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void adminCannotDeleteSelf() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/" + admin1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void adminCanUnlockUser() throws Exception {
        customer.recordFailedAttempt(1, 15, Instant.now());
        userRepository.save(customer);
        assertThat(customer.isLocked()).isTrue();

        mockMvc.perform(put("/api/v1/admin/users/" + customer.getId() + "/unlock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        User unlocked = userRepository.findById(customer.getId()).orElseThrow();
        assertThat(unlocked.isLocked()).isFalse();
    }
}
