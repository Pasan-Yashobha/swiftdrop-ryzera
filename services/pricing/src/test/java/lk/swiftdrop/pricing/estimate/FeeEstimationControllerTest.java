package lk.swiftdrop.pricing.estimate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lk.swiftdrop.pricing.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FeeEstimationController.class)
@Import(SecurityConfig.class)
class FeeEstimationControllerTest {
    static final String PATH = "/api/v1/pricing/estimate";
    static final String BODY = """
            {"originZone":"COLOMBO","destinationZone":"KANDY","weightKg":2,
             "sizeClass":"MEDIUM","deliveryType":"STANDARD"}
            """;
    @Autowired MockMvc mvc;
    @MockitoBean FeeEstimationService estimates;
    @MockitoBean JwtDecoder decoder;

    @Test
    void customerAndMerchantCanEstimateUsingTheirTokenSubject() throws Exception {
        for (String role : new String[]{"CUSTOMER", "MERCHANT"}) {
            mvc.perform(post(PATH).with(jwt().jwt(token -> token.subject(role))
                    .authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                    .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
            verify(estimates).estimate(any(), eq(role));
        }
    }

    @Test
    void anonymousAndCourierAreRejected() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COURIER")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(estimates);
    }

    @Test
    void zeroExcessiveAndNegativeWeightsAreRejected() throws Exception {
        for (String weight : new String[]{"0", "20.001", "-1"}) {
            mvc.perform(post(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                    .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"weightKg\":2", "\"weightKg\":" + weight)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(estimates);
    }

    @Test
    void unsupportedDeliveryTypeIsRejected() throws Exception {
        mvc.perform(post(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("STANDARD", "OVERNIGHT")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(estimates);
    }
}
