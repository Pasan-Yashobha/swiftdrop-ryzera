package com.swiftdrop.reporting.controller;

import com.swiftdrop.reporting.common.ApiErrorWriter;
import com.swiftdrop.reporting.common.GlobalExceptionHandler;
import com.swiftdrop.reporting.config.SecurityConfig;
import com.swiftdrop.reporting.dto.MerchantOrdersResponse;
import com.swiftdrop.reporting.service.ReportQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MerchantReportController.class)
@Import({SecurityConfig.class, ApiErrorWriter.class, GlobalExceptionHandler.class})
class MerchantReportEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportQueryService reportQueryService;

    @MockBean
    private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @Test
    @DisplayName("T6: Non-merchant role (COURIER) is rejected with 403 Forbidden")
    @WithMockUser(roles = "COURIER")
    void testCourierCannotAccessMerchantEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/merchant/reports/orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Merchant role with JWT extracts merchant ID from token claims")
    void testMerchantOrdersExtractsMerchantIdFromJwt() throws Exception {
        UUID merchantId = UUID.randomUUID();
        when(reportQueryService.getMerchantOrders(any(), any(), eq(merchantId)))
                .thenReturn(new MerchantOrdersResponse(merchantId, "Acme Corp", 10, 8, 2, List.of()));

        mockMvc.perform(get("/api/v1/merchant/reports/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"))
                                .jwt(token -> token.claim("merchant_id", merchantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.merchantName").value("Acme Corp"))
                .andExpect(jsonPath("$.totalDelivered").value(8));
    }
}
