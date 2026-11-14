package lk.swiftdrop.pricing.promo;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import lk.swiftdrop.pricing.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PromoCodeController.class)
@Import(SecurityConfig.class)
class PromoCodeControllerTest {
    static final String PATH = "/api/v1/admin/promo-codes";
    @Autowired MockMvc mvc;
    @MockitoBean PromoCodeService promos;
    @MockitoBean JwtDecoder decoder;

    @Test
    void customerCannotManagePromos() throws Exception {
        mvc.perform(get(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(promos);
    }

    @Test
    void anonymousCannotListPromos() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(promos);
    }

    @Test
    void invalidPercentageIsRejected() throws Exception {
        mvc.perform(post(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("""
                  {"code":"SAVE","discountType":"PERCENT","discountValue":101,
                   "minOrderValue":0,"validFrom":"2026-11-12"}
                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(promos);
    }

    @Test
    void adminCanDeactivatePromo() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete(PATH + "/" + id).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());
        verify(promos).deactivate(id);
    }
}
