package lk.swiftdrop.parcel.parcel;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.parcel.common.PageResponse;
import lk.swiftdrop.parcel.parcel.dto.CreateCustomerParcelRequest;
import lk.swiftdrop.parcel.parcel.dto.CustomerParcelDetailResponse;
import lk.swiftdrop.parcel.parcel.dto.CustomerParcelSummaryResponse;
import lk.swiftdrop.parcel.parcel.dto.ParcelStatusHistoryDto;
import lk.swiftdrop.parcel.security.CurrentUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parcels")
public class CustomerParcelController {

    private final ParcelService parcelService;

    public CustomerParcelController(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<CustomerParcelDetailResponse> createDelivery(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateCustomerParcelRequest request) {
        String customerId = CurrentUser.requireUserId();
        CustomerParcelDetailResponse response = parcelService.createCustomerParcel(request, customerId, idempotencyKey);
        URI location = URI.create("/api/v1/parcels/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<CustomerParcelDetailResponse> getParcelDetails(@PathVariable UUID id) {
        String customerId = CurrentUser.requireUserId();
        boolean isAdmin = CurrentUser.isAdmin();
        CustomerParcelDetailResponse response = parcelService.getCustomerParcel(id, customerId, isAdmin);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<PageResponse<CustomerParcelSummaryResponse>> listParcels(
            @RequestParam(required = false) ParcelStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        String customerId = CurrentUser.requireUserId();
        PageResponse<CustomerParcelSummaryResponse> response =
                parcelService.listCustomerParcels(customerId, status, search, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<List<ParcelStatusHistoryDto>> getParcelHistory(@PathVariable UUID id) {
        String customerId = CurrentUser.requireUserId();
        boolean isAdmin = CurrentUser.isAdmin();
        CustomerParcelDetailResponse response = parcelService.getCustomerParcel(id, customerId, isAdmin);
        return ResponseEntity.ok(response.history());
    }
}
