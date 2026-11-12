package lk.swiftdrop.pricing.zone;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.pricing.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ZoneController.class)
@Import(SecurityConfig.class)
class ZoneControllerTest {
    private static final String PATH = "/api/v1/admin/zones";
    private static final String BODY = "{\"code\":\"COLOMBO\",\"name\":\"Colombo\"}";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ZoneService zones;

    @MockitoBean
    JwtDecoder decoder;

    @Test
    void adminCanUpdateZone() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        when(zones.update(any(), any())).thenReturn(
                new ZoneResponse(id, "COLOMBO", "Colombo", null, true, now, now));
        mvc.perform(put(PATH + "/" + id).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
        verify(zones).update(id, new ZoneRequest("COLOMBO", "Colombo", null));
    }

    @Test
    void adminCanDeactivateZone() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete(PATH + "/" + id)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());
        verify(zones).deactivate(id);
    }

    @Test
    void customerCannotUpdateOrDeactivateZones() throws Exception {
        String path = PATH + "/" + UUID.randomUUID();
        mvc.perform(put(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        mvc.perform(delete(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(zones);
    }

    @Test
    void anonymousCannotReadZones() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(zones);
    }

    @Test
    void customerCannotCreateZones() throws Exception {
        mvc.perform(post(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(zones);
    }

    @Test
    void adminClaimInDecodedBearerTokenCanCreateZone() throws Exception {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        when(decoder.decode("admin-token")).thenReturn(Jwt.withTokenValue("admin-token")
                .header("alg", "RS256").subject("admin").claim("role", "ADMIN")
                .issuedAt(now).expiresAt(now.plusSeconds(60)).build());
        when(zones.create(any())).thenReturn(new ZoneResponse(id, "COLOMBO", "Colombo", null, true, now, now));

        mvc.perform(post(PATH).header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", PATH + "/" + id))
                .andExpect(jsonPath("$.code").value("COLOMBO"));
    }

    @Test
    void invalidBodyIsRejectedBeforeSaving() throws Exception {
        mvc.perform(post(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"bad code\",\"name\":\" \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(zones);
    }

    @Test
    void paginationIsBounded() throws Exception {
        mvc.perform(get(PATH).param("size", "1000")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(zones);
    }

    @Test
    void duplicateCodeReturnsSafeConflictResponse() throws Exception {
        when(zones.create(any())).thenThrow(new DataIntegrityViolationException("private database details"));
        mvc.perform(post(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "The zone conflicts with an existing record. Zone codes must be unique."));
    }
}
