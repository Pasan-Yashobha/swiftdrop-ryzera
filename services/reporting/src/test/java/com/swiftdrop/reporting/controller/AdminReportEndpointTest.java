package com.swiftdrop.reporting.controller;

import com.swiftdrop.reporting.common.ApiErrorWriter;
import com.swiftdrop.reporting.common.GlobalExceptionHandler;
import com.swiftdrop.reporting.config.SecurityConfig;
import com.swiftdrop.reporting.dto.SummaryResponse;
import com.swiftdrop.reporting.service.ReportQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminReportController.class)
@Import({SecurityConfig.class, ApiErrorWriter.class, GlobalExceptionHandler.class})
class AdminReportEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportQueryService reportQueryService;

    @MockBean
    private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @Test
    @DisplayName("T6: Unauthenticated request returns 401 Unauthorized")
    void testUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("T6: Wrong role (CUSTOMER) returns 403 Forbidden")
    @WithMockUser(roles = "CUSTOMER")
    void testWrongRoleCustomerReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T6: Authorized role (ADMIN) returns 200 OK")
    @WithMockUser(roles = "ADMIN")
    void testAdminRoleReturns200() throws Exception {
        when(reportQueryService.getSummary(any(), any()))
                .thenReturn(new SummaryResponse(5, 2, 2, 1, 0.5, 0.75, 3L, false, Instant.now()));

        mockMvc.perform(get("/api/v1/admin/reports/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(5))
                .andExpect(jsonPath("$.delivered").value(2))
                .andExpect(jsonPath("$.attemptRate").value(0.5));
    }

    @Test
    @DisplayName("T6: Authorized role (DISPATCHER) returns 200 OK")
    @WithMockUser(roles = "DISPATCHER")
    void testDispatcherRoleReturns200() throws Exception {
        when(reportQueryService.getSummary(any(), any()))
                .thenReturn(new SummaryResponse(5, 2, 2, 1, 0.5, 0.75, 3L, false, Instant.now()));

        mockMvc.perform(get("/api/v1/admin/reports/summary"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CSV export returns text/csv format")
    @WithMockUser(roles = "ADMIN")
    void testCsvExportReturnsTextCsv() throws Exception {
        when(reportQueryService.getSummary(any(), any()))
                .thenReturn(new SummaryResponse(5, 2, 2, 1, 0.5, 0.75, 3L, false, Instant.now()));

        mockMvc.perform(get("/api/v1/admin/reports/summary?format=csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=summary.csv"));
    }
}
