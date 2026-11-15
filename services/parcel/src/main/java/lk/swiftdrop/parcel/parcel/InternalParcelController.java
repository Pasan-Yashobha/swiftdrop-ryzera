package lk.swiftdrop.parcel.parcel;

import jakarta.validation.Valid;
import java.util.UUID;
import lk.swiftdrop.parcel.parcel.dto.CreateMerchantParcelRequest;
import lk.swiftdrop.parcel.parcel.dto.InternalParcelBookingResponse;
import lk.swiftdrop.parcel.parcel.dto.InternalParcelDetailResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/parcels")
public class InternalParcelController {

    private final ParcelService parcelService;

    public InternalParcelController(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    @PostMapping
    public ResponseEntity<InternalParcelBookingResponse> createParcel(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateMerchantParcelRequest request) {
        InternalParcelBookingResponse response = parcelService.createMerchantParcel(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{parcelId}")
    public ResponseEntity<InternalParcelDetailResponse> getParcelDetails(@PathVariable UUID parcelId) {
        InternalParcelDetailResponse response = parcelService.getInternalParcelDetails(parcelId);
        return ResponseEntity.ok(response);
    }
}
