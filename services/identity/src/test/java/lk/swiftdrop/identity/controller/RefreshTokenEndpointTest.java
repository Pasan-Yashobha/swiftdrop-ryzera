package lk.swiftdrop.identity.controller;

import com.fasterxml.jackson.databind.JsonNode;
import lk.swiftdrop.identity.domain.RefreshToken;
import lk.swiftdrop.identity.domain.Role;
import lk.swiftdrop.identity.domain.User;
import lk.swiftdrop.identity.dto.RefreshRequest;
import lk.swiftdrop.identity.repository.RefreshTokenRepository;
import lk.swiftdrop.identity.repository.UserRepository;
import lk.swiftdrop.identity.security.TokenUtils;
import lk.swiftdrop.identity.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefreshTokenEndpointTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void normalRotationIssuesNewTokenPairAndRevokesOld() throws Exception {
        User user = userRepository.save(User.register("Ruwan Fernando", "ruwan@example.com", null, "hash", Role.MERCHANT, Instant.now()));
        String rawToken = TokenUtils.generateRawRefreshToken();
        RefreshToken token = RefreshToken.createNewFamily(user.getId(), TokenUtils.hash(rawToken), Instant.now().plus(7, ChronoUnit.DAYS), Instant.now());
        refreshTokenRepository.save(token);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshRequest(rawToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String newRawToken = responseNode.get("refreshToken").asText();
        assertThat(newRawToken).isNotEqualTo(rawToken);

        // Verify old token is now marked revoked in database
        RefreshToken oldTokenInDb = refreshTokenRepository.findByTokenHash(TokenUtils.hash(rawToken)).orElseThrow();
        assertThat(oldTokenInDb.isRevoked()).isTrue();

        // Verify new token exists and is valid
        RefreshToken newTokenInDb = refreshTokenRepository.findByTokenHash(TokenUtils.hash(newRawToken)).orElseThrow();
        assertThat(newTokenInDb.isRevoked()).isFalse();
        assertThat(newTokenInDb.getFamilyId()).isEqualTo(oldTokenInDb.getFamilyId());
    }

    @Test
    void concurrentRefreshWithinGraceWindowReturns409Conflict() throws Exception {
        User user = userRepository.save(User.register("Ruwan Fernando", "ruwan2@example.com", null, "hash", Role.MERCHANT, Instant.now()));
        String rawToken = TokenUtils.generateRawRefreshToken();
        RefreshToken token = RefreshToken.createNewFamily(user.getId(), TokenUtils.hash(rawToken), Instant.now().plus(7, ChronoUnit.DAYS), Instant.now());
        // Simulate it was just revoked 5 seconds ago
        token.revoke(Instant.now().minusSeconds(5));
        refreshTokenRepository.save(token);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshRequest(rawToken))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_REFRESH"));
    }

    @Test
    void tokenReplayAttackOutsideGraceWindowRevokesEntireFamily() throws Exception {
        User user = userRepository.save(User.register("Ruwan Fernando", "ruwan3@example.com", null, "hash", Role.MERCHANT, Instant.now()));
        UUID familyId = UUID.randomUUID();
        String rawToken = TokenUtils.generateRawRefreshToken();

        // Old token revoked 60 seconds ago (outside 30s grace window)
        RefreshToken oldToken = new RefreshToken(UUID.randomUUID(), user.getId(), familyId, TokenUtils.hash(rawToken),
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(6, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS));
        oldToken.revoke(Instant.now().minusSeconds(60));
        refreshTokenRepository.save(oldToken);

        // Also create a currently active token in that same family
        String activeRaw = TokenUtils.generateRawRefreshToken();
        RefreshToken activeToken = new RefreshToken(UUID.randomUUID(), user.getId(), familyId, TokenUtils.hash(activeRaw),
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(6, ChronoUnit.DAYS), Instant.now().minusSeconds(50));
        refreshTokenRepository.save(activeToken);

        // Attacker replays oldToken
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshRequest(rawToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_COMPROMISED"));

        // Entire family must now be revoked
        RefreshToken checkedActive = refreshTokenRepository.findByTokenHash(TokenUtils.hash(activeRaw)).orElseThrow();
        assertThat(checkedActive.isRevoked()).isTrue();
    }
}
