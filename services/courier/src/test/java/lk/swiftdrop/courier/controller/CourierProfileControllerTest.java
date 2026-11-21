package lk.swiftdrop.courier.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.courier.common.ApiErrorFactory;
import lk.swiftdrop.courier.common.ApiErrorWriter;
import lk.swiftdrop.courier.common.CorrelationIdFilter;
import lk.swiftdrop.courier.common.GlobalExceptionHandler;
import lk.swiftdrop.courier.common.NotFoundException;
import lk.swiftdrop.courier.config.AppProperties;
import lk.swiftdrop.courier.config.ClockConfig;
import lk.swiftdrop.courier.config.SecurityConfig;
import lk.swiftdrop.courier.domain.VehicleType;
import lk.swiftdrop.courier.dto.CourierProfileResponse;
import lk.swiftdrop.courier.dto.UpdateAvailabilityRequest;
import lk.swiftdrop.courier.dto.UpdateProfileRequest;
import lk.swiftdrop.courier.security.JwtRoleConverter;
import lk.swiftdrop.courier.security.RestAccessDeniedHandler;
import lk.swiftdrop.courier.security.RestAuthenticationEntryPoint;
import lk.swiftdrop.courier.service.CourierProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CourierProfileController.class)
@org.springframework.test.context.ActiveProfiles("test")
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
class CourierProfileControllerTest {

    private static final String COURIER_ID_STR = "c0000001-0000-0000-0000-000000000001";
    private static final UUID COURIER_ID = UUID.fromString(COURIER_ID_STR);
    private static final UUID ZONE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @MockBean
    private CourierProfileService profileService;

    @Test
    @DisplayName("GET /api/v1/courier/profile - returns profile for authenticated courier")
    @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void shouldReturnProfileWhenAuthenticated() throws Exception {
        CourierProfileResponse profile = new CourierProfileResponse(
                COURIER_ID, "Kasun Perera", "+94771234567", VehicleType.BIKE,
                "WP BCD-1234", true, 1, 8, ZONE_ID,
                Instant.parse("2026-11-14T10:00:00Z"), Instant.parse("2026-11-14T10:00:00Z")
        );
        when(profileService.getProfile(COURIER_ID)).thenReturn(profile);

        mockMvc.perform(get("/api/v1/courier/profile")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value(COURIER_ID_STR))
                .andExpect(jsonPath("$.fullName").value("Kasun Perera"))
                .andExpect(jsonPath("$.phone").value("+94771234567"))
                .andExpect(jsonPath("$.vehicleType").value("BIKE"))
                .andExpect(jsonPath("$.vehiclePlate").value("WP BCD-1234"))
                .andExpect(jsonPath("$.isAvailable").value(true))
                .andExpect(jsonPath("$.activeDeliveries").value(1))
                .andExpect(jsonPath("$.maxActiveDeliveries").value(8))
                .andExpect(jsonPath("$.assignedZoneId").value(ZONE_ID.toString()));

        verify(profileService).getProfile(COURIER_ID);
    }

    @Test
    @DisplayName("GET /api/v1/courier/profile - returns 404 when profile not found")
    @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void shouldReturn404WhenProfileNotFound() throws Exception {
        when(profileService.getProfile(COURIER_ID))
                .thenThrow(new NotFoundException("Courier profile not found for ID: " + COURIER_ID));

        mockMvc.perform(get("/api/v1/courier/profile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Courier profile not found for ID: " + COURIER_ID));
    }

    @Test
    @DisplayName("GET /api/v1/courier/profile - returns 401 when unauthenticated")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/courier/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/courier/profile - returns 403 when user has wrong role (e.g. CUSTOMER)")
    @WithMockUser(username = COURIER_ID_STR, roles = "CUSTOMER")
    void shouldReturn403WhenWrongRole() throws Exception {
        mockMvc.perform(get("/api/v1/courier/profile"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/v1/courier/profile - successfully updates profile")
    @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void shouldUpdateProfileWhenValid() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest(
                "Nimal Perera", "+94777654321", VehicleType.VAN, "WP CAA-1122", ZONE_ID
        );
        CourierProfileResponse updated = new CourierProfileResponse(
                COURIER_ID, "Nimal Perera", "+94777654321", VehicleType.VAN,
                "WP CAA-1122", true, 0, 8, ZONE_ID,
                Instant.parse("2026-11-14T10:00:00Z"), Instant.parse("2026-11-15T00:00:00Z")
        );
        when(profileService.updateProfile(eq(COURIER_ID), any(UpdateProfileRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/courier/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nimal Perera"))
                .andExpect(jsonPath("$.vehicleType").value("VAN"))
                .andExpect(jsonPath("$.vehiclePlate").value("WP CAA-1122"));

        verify(profileService).updateProfile(eq(COURIER_ID), any(UpdateProfileRequest.class));
    }

    @Test
    @DisplayName("PUT /api/v1/courier/profile - returns 400 when validation fails (blank name or invalid phone)")
    @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void shouldReturn400WhenValidationFails() throws Exception {
        UpdateProfileRequest invalidRequest = new UpdateProfileRequest(
                "", "not-a-phone", null, "WP CAA-1122", ZONE_ID
        );

        mockMvc.perform(put("/api/v1/courier/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("PATCH /api/v1/courier/profile/availability - updates availability status")
    @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void shouldUpdateAvailability() throws Exception {
        UpdateAvailabilityRequest request = new UpdateAvailabilityRequest(false);
        CourierProfileResponse updated = new CourierProfileResponse(
                COURIER_ID, "Kasun Perera", "+94771234567", VehicleType.BIKE,
                "WP BCD-1234", false, 0, 8, ZONE_ID,
                Instant.parse("2026-11-14T10:00:00Z"), Instant.parse("2026-11-15T00:00:00Z")
        );
        when(profileService.updateAvailability(COURIER_ID, false)).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/courier/profile/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(false));

        verify(profileService).updateAvailability(COURIER_ID, false);
    }

    @Test
    @DisplayName("PATCH /api/v1/courier/profile/availability - returns 400 when available is null")
    @WithMockUser(username = COURIER_ID_STR, roles = "COURIER")
    void shouldReturn400WhenAvailabilityNull() throws Exception {
        mockMvc.perform(patch("/api/v1/courier/profile/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
