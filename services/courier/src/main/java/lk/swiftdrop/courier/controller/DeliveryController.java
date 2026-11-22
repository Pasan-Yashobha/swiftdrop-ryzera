package lk.swiftdrop.courier.controller;

import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.courier.dto.AssignedDeliveryResponse;
import lk.swiftdrop.courier.dto.DeliveryStatusResponse;
import lk.swiftdrop.courier.dto.DeliveryStatusUpdateRequest;
import lk.swiftdrop.courier.security.CurrentUser;
import lk.swiftdrop.courier.service.DeliveryService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/courier/deliveries")
@PreAuthorize("hasAnyRole('COURIER', 'ADMIN')")
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final Validator validator;

    public DeliveryController(DeliveryService deliveryService, Validator validator) {
        this.deliveryService = deliveryService;
        this.validator = validator;
    }

    @GetMapping("/today")
    public ResponseEntity<List<AssignedDeliveryResponse>> getTodaysDeliveries() {
        UUID courierId = CurrentUser.current().userId();
        List<AssignedDeliveryResponse> deliveries = deliveryService.getTodaysDeliveries(courierId);
        return ResponseEntity.ok(deliveries);
    }

    /**
     * Submits delivery status transition via JSON payload.
     * Used for PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, FAILED, or pre-uploaded proof DELIVERED.
     */
    @PatchMapping(value = "/{parcelId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DeliveryStatusResponse> updateStatusJson(
            @PathVariable UUID parcelId,
            @Valid @RequestBody DeliveryStatusUpdateRequest request) {
        UUID courierId = CurrentUser.current().userId();
        DeliveryStatusResponse response = deliveryService.updateDeliveryStatus(courierId, parcelId, request, null);
        return ResponseEntity.ok(response);
    }

    /**
     * Submits delivery status transition with multipart photo upload.
     * Used especially for DELIVERED status with proof photo attachment.
     */
    @PatchMapping(value = "/{parcelId}/status", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DeliveryStatusResponse> updateStatusMultipart(
            @PathVariable UUID parcelId,
            @RequestParam("status") String status,
            @RequestParam(value = "recipientName", required = false) String recipientName,
            @RequestParam(value = "failureReason", required = false) String failureReason,
            @RequestParam(value = "otp", required = false) String otp,
            @RequestParam(value = "photoObjectKey", required = false) String photoObjectKey,
            @RequestPart(value = "proofPhoto", required = false) MultipartFile proofPhoto,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        UUID courierId = CurrentUser.current().userId();
        if (proofPhoto != null && photo != null) {
            throw new lk.swiftdrop.courier.common.BadRequestException("Supply only one proof photo upload");
        }
        MultipartFile file = proofPhoto != null ? proofPhoto : photo;
        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest(
                status, recipientName, failureReason, otp, photoObjectKey);
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        DeliveryStatusResponse response = deliveryService.updateDeliveryStatus(courierId, parcelId, request, file);
        return ResponseEntity.ok(response);
    }
}
