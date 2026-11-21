package lk.swiftdrop.courier.controller;

import jakarta.validation.Valid;
import lk.swiftdrop.courier.dto.LocationReportRequest;
import lk.swiftdrop.courier.security.CurrentUser;
import lk.swiftdrop.courier.service.CourierLocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/courier/location")
@PreAuthorize("hasAnyRole('COURIER', 'ADMIN')")
public class CourierLocationController {
    private final CourierLocationService service;
    public CourierLocationController(CourierLocationService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<Void> report(@Valid @RequestBody LocationReportRequest request) {
        service.report(CurrentUser.current().userId(), request);
        return ResponseEntity.noContent().build();
    }
}
