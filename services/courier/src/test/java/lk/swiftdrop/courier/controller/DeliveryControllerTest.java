package lk.swiftdrop.courier.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.courier.common.ApiErrorFactory;
import lk.swiftdrop.courier.common.ApiErrorWriter;
import lk.swiftdrop.courier.common.BadRequestException;
import lk.swiftdrop.courier.common.CorrelationIdFilter;
import lk.swiftdrop.courier.common.ForbiddenException;
import lk.swiftdrop.courier.common.GlobalExceptionHandler;
import lk.swiftdrop.courier.common.NotFoundException;
import lk.swiftdrop.courier.config.AppProperties;
import lk.swiftdrop.courier.config.ClockConfig;
import lk.swiftdrop.courier.config.SecurityConfig;
import lk.swiftdrop.courier.domain.AssignmentStatus;
import lk.swiftdrop.courier.dto.AssignedDeliveryResponse;
import lk.swiftdrop.courier.dto.DeliveryStatusResponse;
import lk.swiftdrop.courier.dto.DeliveryStatusUpdateRequest;
import lk.swiftdrop.courier.security.JwtRoleConverter;
import lk.swiftdrop.courier.security.RestAccessDeniedHandler;
import lk.swiftdrop.courier.security.RestAuthenticationEntryPoint;
import lk.swiftdrop.courier.service.DeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeliveryController.class)
@ActiveProfiles("test")
@EnableConfigurationProperties(AppProperties.class)
@Import({
        SecurityConfig.class,
        JwtRoleConverter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        ApiErrorWriter.class,
        ApiErrorFactory.class,
        ClockConfig.class,
        CorrelationIdFilter.class,
        GlobalExceptionHandler.class
})
class DeliveryControllerTest {

    private static final String COURIER_ID_STR = "c0000001-0000-0000-0000-000000000001";
    private static final UUID COURIER_ID = UUID.fromString(COURIER_ID_STR);
    private static final UUID PARCEL_ID = UUID.fromString("b0000001-0000-0000-0000-000000000001");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("a0000001-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private DeliveryService deliveryService;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "COURIER_MISMATCH,409", "IDEMPOTENCY_KEY_IN_USE,409", "OPTIMISTIC_LOCK,409",
            "IDEMPOTENCY_KEY_REUSED,422", "PARCEL_REJECTED,422"
    })
    @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void parcelErrorsRetainStatusCodeAndRequestCorrelation(lk.swiftdrop.courier.common.ErrorCode code, int statusCode)
            throws Exception {
        when(deliveryService.updateDeliveryStatus(eq(COURIER_ID), eq(PARCEL_ID), any(), isNull()))
                .thenThrow(new lk.swiftdrop.courier.common.ApiException(code, "Rejected by Parcel"));
        mockMvc.perform(patch("/api/v1/courier/deliveries/" + PARCEL_ID + "/status")
                        .header("X-Correlation-Id", "hardening-contract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PICKED_UP\",\"courierId\":\"untrusted-frontend-id\"}"))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.correlationId").value("hardening-contract"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Correlation-Id", "hardening-contract"));
        verify(deliveryService).updateDeliveryStatus(eq(COURIER_ID), eq(PARCEL_ID), any(), isNull());
    }

    @Test
    @DisplayName("GET /api/v1/courier/deliveries/today - returns today's assigned deliveries for authenticated courier")
    @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void shouldReturnTodaysDeliveriesWhenAuthenticatedAsCourier() throws Exception {
        AssignedDeliveryResponse item = new AssignedDeliveryResponse(
                ASSIGNMENT_ID,
                PARCEL_ID,
                COURIER_ID,
                "SWD-COL-1001",
                AssignmentStatus.ASSIGNED,
                Instant.parse("2026-11-15T08:00:00Z"),
                null,
                null
        );

        when(deliveryService.getTodaysDeliveries(COURIER_ID)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/courier/deliveries/today")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].assignmentId").value(ASSIGNMENT_ID.toString()))
                .andExpect(jsonPath("$[0].parcelId").value(PARCEL_ID.toString()))
                .andExpect(jsonPath("$[0].courierId").value(COURIER_ID_STR))
                .andExpect(jsonPath("$[0].trackingNo").value("SWD-COL-1001"))
                .andExpect(jsonPath("$[0].status").value("ASSIGNED"));

        verify(deliveryService).getTodaysDeliveries(COURIER_ID);
    }

    @Test
    @DisplayName("GET /api/v1/courier/deliveries/today - returns empty list 200 OK when no deliveries assigned")
    @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void shouldReturnEmptyListWhenNoDeliveries() throws Exception {
        when(deliveryService.getTodaysDeliveries(COURIER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/courier/deliveries/today")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/courier/deliveries/today - returns 401 when unauthenticated")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/courier/deliveries/today")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("GET /api/v1/courier/deliveries/today - returns 403 when authenticated with non-courier role")
    @WithMockUser(username = "merchant-user", roles = "MERCHANT")
    void shouldReturn403WhenRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/courier/deliveries/today")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("PATCH /api/v1/courier/deliveries/{parcelId}/status (JSON) - 200 OK on valid status update")
    @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void patchStatusJson_success() throws Exception {
        DeliveryStatusResponse response = new DeliveryStatusResponse(
                PARCEL_ID, "SWD-COL-1001", "PICKED_UP", "IN_TRANSIT", Instant.parse("2026-11-15T08:00:00Z"));

        when(deliveryService.updateDeliveryStatus(eq(COURIER_ID), eq(PARCEL_ID), any(DeliveryStatusUpdateRequest.class), isNull()))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/courier/deliveries/{parcelId}/status", PARCEL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "IN_TRANSIT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelId").value(PARCEL_ID.toString()))
                .andExpect(jsonPath("$.trackingNo").value("SWD-COL-1001"))
                .andExpect(jsonPath("$.previousStatus").value("PICKED_UP"))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));

        verify(deliveryService).updateDeliveryStatus(eq(COURIER_ID), eq(PARCEL_ID), any(DeliveryStatusUpdateRequest.class), isNull());
    }

    @Test
    @DisplayName("PATCH /api/v1/courier/deliveries/{parcelId}/status (JSON) - 400 Bad Request when status is blank")
    @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void patchStatusJson_validationFailureWhenStatusBlank() throws Exception {
        mockMvc.perform(patch("/api/v1/courier/deliveries/{parcelId}/status", PARCEL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/courier/deliveries/{parcelId}/status (JSON) - 403 Forbidden when parcel belongs to another courier")
    @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void patchStatusJson_forbiddenWhenAssignedToAnother() throws Exception {
        when(deliveryService.updateDeliveryStatus(eq(COURIER_ID), eq(PARCEL_ID), any(DeliveryStatusUpdateRequest.class), isNull()))
                .thenThrow(new ForbiddenException("Parcel is assigned to another courier"));

        mockMvc.perform(patch("/api/v1/courier/deliveries/{parcelId}/status", PARCEL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "IN_TRANSIT"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("PATCH /api/v1/courier/deliveries/{parcelId}/status (JSON) - 404 Not Found when assignment not found")
    @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void patchStatusJson_notFoundWhenAssignmentMissing() throws Exception {
        when(deliveryService.updateDeliveryStatus(eq(COURIER_ID), eq(PARCEL_ID), any(DeliveryStatusUpdateRequest.class), isNull()))
                .thenThrow(new NotFoundException("Delivery assignment not found for parcel " + PARCEL_ID));

        mockMvc.perform(patch("/api/v1/courier/deliveries/{parcelId}/status", PARCEL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "IN_TRANSIT"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /api/v1/courier/deliveries/{parcelId}/status (Multipart) - 200 OK on DELIVERED with proof photo")
    @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void patchStatusMultipart_deliveredSuccess() throws Exception {
        MockMultipartFile photo = new MockMultipartFile(
                "proofPhoto", "proof.jpg", "image/jpeg", "dummy-image-bytes".getBytes());

        DeliveryStatusResponse response = new DeliveryStatusResponse(
                PARCEL_ID, "SWD-COL-1001", "OUT_FOR_DELIVERY", "DELIVERED", Instant.parse("2026-11-15T08:00:00Z"));

        when(deliveryService.updateDeliveryStatus(eq(COURIER_ID), eq(PARCEL_ID), any(DeliveryStatusUpdateRequest.class), any()))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/v1/courier/deliveries/{parcelId}/status", PARCEL_ID)
                        .file(photo)
                        .param("status", "DELIVERED")
                        .param("recipientName", "Kamal Perera")
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.parcelId").value(PARCEL_ID.toString()));
    }

    @Test
    @DisplayName("PATCH /api/v1/courier/deliveries/{parcelId}/status (Multipart) - 400 Bad Request when recipientName missing on DELIVERED")
    @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void patchStatusMultipart_badRequestWhenValidationFailsInService() throws Exception {
        when(deliveryService.updateDeliveryStatus(eq(COURIER_ID), eq(PARCEL_ID), any(DeliveryStatusUpdateRequest.class), any()))
                .thenThrow(new BadRequestException("recipientName is required when status is DELIVERED"));

        mockMvc.perform(multipart("/api/v1/courier/deliveries/{parcelId}/status", PARCEL_ID)
                        .param("status", "DELIVERED")
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("recipientName is required when status is DELIVERED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/courier/deliveries/{parcelId}/status - 401 Unauthorized when unauthenticated")
    void patchStatus_unauthenticated() throws Exception {
        mockMvc.perform(patch("/api/v1/courier/deliveries/{parcelId}/status", PARCEL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "IN_TRANSIT"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
    static java.util.stream.Stream<String> invalidLimitedFields() {
        return java.util.stream.Stream.of("recipientName", "photoObjectKey", "otp", "failureReason");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("invalidLimitedFields")
    @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void jsonAndMultipartEnforceIdenticalFieldLimits(String field) throws Exception {
        int maximum = switch (field) {
            case "recipientName" -> 100;
            case "photoObjectKey" -> 255;
            case "otp" -> 10;
            default -> 500;
        };
        String value = "x".repeat(maximum + 1);
        var json = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        json.put("status", "PICKED_UP").put(field, value);
        mockMvc.perform(patch("/api/v1/courier/deliveries/{parcelId}/status", PARCEL_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(json.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(multipart("/api/v1/courier/deliveries/{parcelId}/status", PARCEL_ID)
                        .param("status", "PICKED_UP").param(field, value)
                        .with(request -> { request.setMethod("PATCH"); return request; }))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        org.mockito.Mockito.verifyNoInteractions(deliveryService);
    }

    @Test
    @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void identityAlwaysComesFromAuthenticatedSubjectNotFrontendCourierId() throws Exception {
        UUID attackerSupplied = UUID.randomUUID();
        when(deliveryService.updateDeliveryStatus(eq(COURIER_ID), eq(PARCEL_ID), any(), isNull()))
                .thenReturn(new DeliveryStatusResponse(PARCEL_ID, "SWD-1", "ASSIGNED", "PICKED_UP", Instant.now()));
        mockMvc.perform(patch("/api/v1/courier/deliveries/{parcelId}/status", PARCEL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PICKED_UP\",\"courierId\":\"" + attackerSupplied + "\"}"))
                .andExpect(status().isOk());
        verify(deliveryService).updateDeliveryStatus(eq(COURIER_ID), eq(PARCEL_ID), any(), isNull());
        when(deliveryService.getTodaysDeliveries(COURIER_ID)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/courier/deliveries/today").param("courierId", attackerSupplied.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        verify(deliveryService).getTodaysDeliveries(COURIER_ID);
    }

    @Test
    @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void nulRecipientIsRejectedOnBothPaths() throws Exception {
        String recipient = "Name" + (char) 0;
        var json = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        json.put("status", "DELIVERED").put("recipientName", recipient);
        mockMvc.perform(patch("/api/v1/courier/deliveries/{parcelId}/status", PARCEL_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(json.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(multipart("/api/v1/courier/deliveries/{parcelId}/status", PARCEL_ID)
                        .param("status", "DELIVERED").param("recipientName", recipient)
                        .with(request -> { request.setMethod("PATCH"); return request; }))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        org.mockito.Mockito.verifyNoInteractions(deliveryService);
    }

    @Test
    @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void twoPhotoAliasesAreRejectedInsteadOfSilentlyIgnoringOne() throws Exception {
        mockMvc.perform(multipart("/api/v1/courier/deliveries/{parcelId}/status", PARCEL_ID)
                        .file(new MockMultipartFile("proofPhoto", "a.png", "image/png", new byte[]{1}))
                        .file(new MockMultipartFile("photo", "b.png", "image/png", new byte[]{2}))
                        .param("status", "DELIVERED").param("recipientName", "Name")
                        .with(request -> { request.setMethod("PATCH"); return request; }))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        org.mockito.Mockito.verifyNoInteractions(deliveryService);
    }


    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"courier-user", "1-1-1-1-1"})
    void invalidAuthenticatedSubjectReturns401WithoutCallingDeliveryService(String subject) throws Exception {
        mockMvc.perform(get("/api/v1/courier/deliveries/today")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(subject).roles("COURIER"))
                        .header("X-Correlation-Id", "invalid-subject-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").value("invalid-subject-test"));
        org.mockito.Mockito.verifyNoInteractions(deliveryService);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"COURIER", "ADMIN"})
    void validUuidSubjectKeepsCourierAndAdminAccess(String role) throws Exception {
        when(deliveryService.getTodaysDeliveries(COURIER_ID)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/courier/deliveries/today")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(COURIER_ID_STR).roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        verify(deliveryService).getTodaysDeliveries(COURIER_ID);
    }
}
