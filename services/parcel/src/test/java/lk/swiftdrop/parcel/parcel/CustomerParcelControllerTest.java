package lk.swiftdrop.parcel.parcel;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import lk.swiftdrop.parcel.common.ApiErrorFactory;
import lk.swiftdrop.parcel.common.ConflictException;
import lk.swiftdrop.parcel.common.ErrorCode;
import lk.swiftdrop.parcel.common.ForbiddenException;
import lk.swiftdrop.parcel.common.GlobalExceptionHandler;
import lk.swiftdrop.parcel.common.NotFoundException;
import lk.swiftdrop.parcel.parcel.dto.AddressDto;
import lk.swiftdrop.parcel.parcel.dto.CancelParcelRequest;
import lk.swiftdrop.parcel.parcel.dto.CreateCustomerParcelRequest;
import lk.swiftdrop.parcel.parcel.dto.CustomerParcelDetailResponse;
import lk.swiftdrop.parcel.parcel.dto.RecipientDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomerParcelControllerTest {

    @Mock
    private ParcelService parcelService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        var errorFactory = new ApiErrorFactory(Clock.systemUTC());
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerParcelController(parcelService))
                .setControllerAdvice(new GlobalExceptionHandler(errorFactory))
                .build();

        var auth = new UsernamePasswordAuthenticationToken(
                "cust-user-1", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("POST /api/v1/parcels returns 201 Created on valid booking request without Idempotency-Key")
    void testCreateDeliveryReturnsCreatedWithoutIdempotencyKey() throws Exception {
        UUID quoteId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();

        var recipient = new RecipientDto("John Doe", "+94771234567", "john@example.com");
        var pickup = new AddressDto("10 Galle Road", "Colombo", "00300");
        var dropoff = new AddressDto("45 Temple Street", "Kandy", "20000");
        var request = new CreateCustomerParcelRequest(
                recipient, pickup, dropoff, new BigDecimal("2.50"), ParcelSize.MEDIUM,
                "Electronics", DeliveryType.STANDARD, "Fragile", quoteId
        );

        var pricing = new CustomerParcelDetailResponse.PricingBreakdownDto(
                quoteId, new BigDecimal("300.00"), new BigDecimal("100.00"), new BigDecimal("50.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("450.00"), "LKR"
        );
        var detailResponse = new CustomerParcelDetailResponse(
                parcelId, "SD26000100ABC", ParcelStatus.CREATED, recipient, pickup, dropoff,
                new BigDecimal("2.50"), ParcelSize.MEDIUM, DeliveryType.STANDARD, "Electronics", "Fragile",
                pricing, null, null, null, Instant.now(), Instant.now(), Collections.emptyList()
        );

        when(parcelService.createCustomerParcel(any(), eq("cust-user-1"), isNull())).thenReturn(detailResponse);

        mockMvc.perform(post("/api/v1/parcels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/parcels/" + parcelId))
                .andExpect(jsonPath("$.id").value(parcelId.toString()))
                .andExpect(jsonPath("$.trackingNumber").value("SD26000100ABC"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.pricing.totalFee").value(450.00));
    }

    @Test
    @DisplayName("POST /api/v1/parcels passes Idempotency-Key header to service")
    void testCreateDeliveryWithIdempotencyKey() throws Exception {
        UUID quoteId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();

        var recipient = new RecipientDto("John Doe", "+94771234567", "john@example.com");
        var pickup = new AddressDto("10 Galle Road", "Colombo", "00300");
        var dropoff = new AddressDto("45 Temple Street", "Kandy", "20000");
        var request = new CreateCustomerParcelRequest(
                recipient, pickup, dropoff, new BigDecimal("2.50"), ParcelSize.MEDIUM,
                "Electronics", DeliveryType.STANDARD, "Fragile", quoteId
        );

        var pricing = new CustomerParcelDetailResponse.PricingBreakdownDto(
                quoteId, new BigDecimal("300.00"), new BigDecimal("100.00"), new BigDecimal("50.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("450.00"), "LKR"
        );
        var detailResponse = new CustomerParcelDetailResponse(
                parcelId, "SD26000100ABC", ParcelStatus.CREATED, recipient, pickup, dropoff,
                new BigDecimal("2.50"), ParcelSize.MEDIUM, DeliveryType.STANDARD, "Electronics", "Fragile",
                pricing, null, null, null, Instant.now(), Instant.now(), Collections.emptyList()
        );

        when(parcelService.createCustomerParcel(any(), eq("cust-user-1"), eq("idem-key-123"))).thenReturn(detailResponse);

        mockMvc.perform(post("/api/v1/parcels")
                        .header("Idempotency-Key", "idem-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/parcels/" + parcelId))
                .andExpect(jsonPath("$.id").value(parcelId.toString()))
                .andExpect(jsonPath("$.trackingNumber").value("SD26000100ABC"));
    }

    @Test
    @DisplayName("GET /api/v1/parcels/{id} returns 403 Forbidden when unauthorized customer accesses parcel")
    void testGetParcelForbidden() throws Exception {
        UUID parcelId = UUID.randomUUID();
        when(parcelService.getCustomerParcel(eq(parcelId), eq("cust-user-1"), eq(false)))
                .thenThrow(new ForbiddenException("You do not have permission to view this parcel"));

        mockMvc.perform(get("/api/v1/parcels/{id}", parcelId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("GET /api/v1/parcels/{id} returns 404 Not Found when parcel does not exist")
    void testGetParcelNotFound() throws Exception {
        UUID parcelId = UUID.randomUUID();
        when(parcelService.getCustomerParcel(eq(parcelId), eq("cust-user-1"), eq(false)))
                .thenThrow(new NotFoundException("Parcel not found"));

        mockMvc.perform(get("/api/v1/parcels/{id}", parcelId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /api/v1/parcels/{id}/cancel cancels parcel and returns 200 with updated details")
    void testCancelParcelSuccess() throws Exception {
        UUID parcelId = UUID.randomUUID();
        var cancelReq = new CancelParcelRequest("Customer requested cancellation");

        var recipient = new RecipientDto("John Doe", "+94771234567", "john@example.com");
        var pickup = new AddressDto("10 Galle Road", "Colombo", "00300");
        var dropoff = new AddressDto("45 Temple Street", "Kandy", "20000");
        var pricing = new CustomerParcelDetailResponse.PricingBreakdownDto(
                UUID.randomUUID(), new BigDecimal("300.00"), new BigDecimal("100.00"), new BigDecimal("50.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("450.00"), "LKR"
        );
        var detailResponse = new CustomerParcelDetailResponse(
                parcelId, "SD26000100ABC", ParcelStatus.CANCELLED, recipient, pickup, dropoff,
                new BigDecimal("2.50"), ParcelSize.MEDIUM, DeliveryType.STANDARD, "Electronics", "Fragile",
                pricing, null, "Customer requested cancellation", null, Instant.now(), Instant.now(), Collections.emptyList()
        );

        when(parcelService.cancelCustomerParcel(eq(parcelId), eq("cust-user-1"), eq(false), eq("Customer requested cancellation")))
                .thenReturn(detailResponse);

        mockMvc.perform(post("/api/v1/parcels/{id}/cancel", parcelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(parcelId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.failureReason").value("Customer requested cancellation"));
    }

    @Test
    @DisplayName("POST /api/v1/parcels/{id}/cancel without body cancels parcel")
    void testCancelParcelWithoutBody() throws Exception {
        UUID parcelId = UUID.randomUUID();

        var recipient = new RecipientDto("John Doe", "+94771234567", "john@example.com");
        var pickup = new AddressDto("10 Galle Road", "Colombo", "00300");
        var dropoff = new AddressDto("45 Temple Street", "Kandy", "20000");
        var pricing = new CustomerParcelDetailResponse.PricingBreakdownDto(
                UUID.randomUUID(), new BigDecimal("300.00"), new BigDecimal("100.00"), new BigDecimal("50.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("450.00"), "LKR"
        );
        var detailResponse = new CustomerParcelDetailResponse(
                parcelId, "SD26000100ABC", ParcelStatus.CANCELLED, recipient, pickup, dropoff,
                new BigDecimal("2.50"), ParcelSize.MEDIUM, DeliveryType.STANDARD, "Electronics", "Fragile",
                pricing, null, null, null, Instant.now(), Instant.now(), Collections.emptyList()
        );

        when(parcelService.cancelCustomerParcel(eq(parcelId), eq("cust-user-1"), eq(false), isNull()))
                .thenReturn(detailResponse);

        mockMvc.perform(post("/api/v1/parcels/{id}/cancel", parcelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(parcelId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("POST /api/v1/parcels/{id}/cancel returns 403 Forbidden when customer does not own parcel")
    void testCancelParcelForbidden() throws Exception {
        UUID parcelId = UUID.randomUUID();
        when(parcelService.cancelCustomerParcel(eq(parcelId), eq("cust-user-1"), eq(false), any()))
                .thenThrow(new ForbiddenException("You do not have permission to cancel this parcel"));

        mockMvc.perform(post("/api/v1/parcels/{id}/cancel", parcelId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /api/v1/parcels/{id}/cancel returns 409 Conflict when parcel is already cancelled or picked up")
    void testCancelParcelConflict() throws Exception {
        UUID parcelId = UUID.randomUUID();
        when(parcelService.cancelCustomerParcel(eq(parcelId), eq("cust-user-1"), eq(false), any()))
                .thenThrow(new ConflictException(ErrorCode.INVALID_STATE, "Parcel is already cancelled"));

        mockMvc.perform(post("/api/v1/parcels/{id}/cancel", parcelId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }
}
