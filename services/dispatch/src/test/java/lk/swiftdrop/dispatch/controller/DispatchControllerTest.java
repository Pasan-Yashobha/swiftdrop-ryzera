package lk.swiftdrop.dispatch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.dispatch.common.ApiErrorFactory;
import lk.swiftdrop.dispatch.common.ApiErrorWriter;
import lk.swiftdrop.dispatch.common.CorrelationIdFilter;
import lk.swiftdrop.dispatch.common.GlobalExceptionHandler;
import lk.swiftdrop.dispatch.config.AppProperties;
import lk.swiftdrop.dispatch.config.ClockConfig;
import lk.swiftdrop.dispatch.config.SecurityConfig;
import lk.swiftdrop.dispatch.domain.AssignmentStatus;
import lk.swiftdrop.dispatch.domain.StrategyType;
import lk.swiftdrop.dispatch.dto.AssignmentResponse;
import lk.swiftdrop.dispatch.dto.CourierWorkloadResponse;
import lk.swiftdrop.dispatch.dto.ManualAssignRequest;
import lk.swiftdrop.dispatch.dto.ReassignRequest;
import lk.swiftdrop.dispatch.dto.UnassignedParcel;
import lk.swiftdrop.dispatch.security.JwtRoleConverter;
import lk.swiftdrop.dispatch.security.RestAccessDeniedHandler;
import lk.swiftdrop.dispatch.security.RestAuthenticationEntryPoint;
import lk.swiftdrop.dispatch.service.AssignmentService;
import lk.swiftdrop.dispatch.service.IdempotencyService;
import lk.swiftdrop.dispatch.service.SseEmitterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DispatchController.class)
@Import({
        SecurityConfig.class,
        JwtRoleConverter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        ApiErrorWriter.class,
        ApiErrorFactory.class,
        ClockConfig.class,
        CorrelationIdFilter.class,
        GlobalExceptionHandler.class,
        AppProperties.class
})
class DispatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @MockBean
    private AssignmentService assignmentService;

    @MockBean
    private IdempotencyService idempotencyService;

    @MockBean
    private SseEmitterService sseEmitterService;

    private final UUID parcelId = UUID.randomUUID();
    private final UUID courierId = UUID.randomUUID();

    @Test
    @DisplayName("Unauthenticated request to dispatch endpoint returns 401 Unauthorized")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/parcels/unassigned"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("CUSTOMER role cannot access dispatch endpoints (returns 403 Forbidden)")
    void customerRoleReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/parcels/unassigned"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @WithMockUser(roles = "COURIER")
    @DisplayName("COURIER role cannot access dispatch endpoints (returns 403 Forbidden)")
    void courierRoleReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/couriers/workload"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "DISPATCHER")
    @DisplayName("DISPATCHER role can fetch unassigned parcels (returns 200 OK)")
    void dispatcherCanFetchUnassignedParcels() throws Exception {
        UnassignedParcel parcel = new UnassignedParcel(parcelId, "SWD-101", null, "Colombo", "SMALL", 1.2, Instant.now());
        when(assignmentService.getUnassignedParcels(any(), any(), eq(0), eq(50))).thenReturn(List.of(parcel));

        mockMvc.perform(get("/api/v1/dispatch/parcels/unassigned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].trackingNumber").value("SWD-101"));
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "DISPATCHER")
    @DisplayName("Manual assignment returns 201 Created and saves idempotency key")
    void manualAssignReturns201() throws Exception {
        ManualAssignRequest request = new ManualAssignRequest(parcelId, courierId);
        AssignmentResponse response = new AssignmentResponse(UUID.randomUUID(), parcelId, courierId,
                StrategyType.MANUAL, AssignmentStatus.ACTIVE, UUID.randomUUID(), Instant.now(), null);

        when(assignmentService.assignManual(eq(parcelId), eq(courierId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/dispatch/assignments")
                        .header("Idempotency-Key", "test-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parcelId").value(parcelId.toString()))
                .andExpect(jsonPath("$.courierId").value(courierId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(idempotencyService).put(eq("test-key-123"), any());
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "DISPATCHER")
    @DisplayName("Manual assignment returns cached response when Idempotency-Key matches")
    void returnsCachedResponseOnRepeatedIdempotencyKey() throws Exception {
        ManualAssignRequest request = new ManualAssignRequest(parcelId, courierId);
        AssignmentResponse cachedResponse = new AssignmentResponse(UUID.randomUUID(), parcelId, courierId,
                StrategyType.MANUAL, AssignmentStatus.ACTIVE, UUID.randomUUID(), Instant.now(), null);

        when(idempotencyService.get("cached-key-abc")).thenReturn(cachedResponse);

        mockMvc.perform(post("/api/v1/dispatch/assignments")
                        .header("Idempotency-Key", "cached-key-abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parcelId").value(parcelId.toString()));
    }

    @Test
    @WithMockUser(roles = "DISPATCHER")
    @DisplayName("Validation failure on missing fields returns 400 Bad Request")
    void validationErrorReturns400() throws Exception {
        // Missing parcelId and courierId
        mockMvc.perform(post("/api/v1/dispatch/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "DISPATCHER")
    @DisplayName("Reassignment returns 200 OK")
    void reassignReturns200() throws Exception {
        UUID newCourierId = UUID.randomUUID();
        ReassignRequest request = new ReassignRequest(newCourierId, "Courier unavailable due to heavy rain");
        AssignmentResponse response = new AssignmentResponse(UUID.randomUUID(), parcelId, newCourierId,
                StrategyType.MANUAL, AssignmentStatus.ACTIVE, UUID.randomUUID(), Instant.now(), request.reason());

        when(assignmentService.reassign(eq(parcelId), eq(newCourierId), eq(request.reason()), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/dispatch/assignments/" + parcelId + "/reassign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value(newCourierId.toString()))
                .andExpect(jsonPath("$.reason").value(request.reason()));
    }
}

