package lk.swiftdrop.parcel.parcel;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import lk.swiftdrop.parcel.common.ApiErrorFactory;
import lk.swiftdrop.parcel.common.ForbiddenException;
import lk.swiftdrop.parcel.common.GlobalExceptionHandler;
import lk.swiftdrop.parcel.common.NotFoundException;
import lk.swiftdrop.parcel.parcel.dto.AddressDto;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @DisplayName("POST /api/v1/parcels returns 201 Created on valid booking request")
    void testCreateDeliveryReturnsCreated() throws Exception {
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

        when(parcelService.createCustomerParcel(any(), eq("cust-user-1"))).thenReturn(detailResponse);

        mockMvc.perform(post("/api/v1/parcels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(parcelId.toString()))
                .andExpect(jsonPath("$.trackingNumber").value("SD26000100ABC"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.pricing.totalFee").value(450.00));
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
}
