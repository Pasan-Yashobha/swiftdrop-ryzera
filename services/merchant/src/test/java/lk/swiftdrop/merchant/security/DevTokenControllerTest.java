package lk.swiftdrop.merchant.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** The dev profile: tokens are issued by this service and accepted by the real HS256 decoder. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
@TestPropertySource(properties = "swiftdrop.security.jwt-issuer=swiftdrop-dev")
class DevTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void issuedMerchantTokenIsAcceptedByTheMerchantEndpoints() throws Exception {
        String token = issueToken("user-1", "MERCHANT");

        mockMvc.perform(get("/api/v1/merchants/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_REGISTERED"));
        mockMvc.perform(get("/api/v1/admin/merchants").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void issuedAdminTokenOpensTheAdminEndpoints() throws Exception {
        String token = issueToken("admin-1", "ADMIN");

        mockMvc.perform(get("/api/v1/admin/merchants").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/merchants/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void responseDescribesTheToken() throws Exception {
        mockMvc.perform(post("/api/v1/dev/tokens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sub\":\"user-1\",\"role\":\"MERCHANT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = issueToken("user-1", "MERCHANT");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        mockMvc.perform(get("/api/v1/merchants/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void requestsAreValidated() throws Exception {
        mockMvc.perform(post("/api/v1/dev/tokens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sub\":\"\",\"role\":\"MERCHANT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("sub"));
        mockMvc.perform(post("/api/v1/dev/tokens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sub\":\"user-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("role"));
        mockMvc.perform(post("/api/v1/dev/tokens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sub\":\"user-1\",\"role\":\"ROOT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.details[0].field").value("role"));
    }

    private String issueToken(String sub, String role) throws Exception {
        String body = mockMvc.perform(post("/api/v1/dev/tokens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sub\":\"" + sub + "\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
