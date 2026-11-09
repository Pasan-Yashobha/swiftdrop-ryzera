package lk.swiftdrop.identity.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lk.swiftdrop.identity.domain.Role;
import lk.swiftdrop.identity.dto.ChangeRoleRequest;
import lk.swiftdrop.identity.dto.PageResponse;
import lk.swiftdrop.identity.dto.UserResponse;
import lk.swiftdrop.identity.security.CurrentUser;
import lk.swiftdrop.identity.service.AdminService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminService adminService;
    private final CurrentUser currentUser;

    public AdminUserController(AdminService adminService, CurrentUser currentUser) {
        this.adminService = adminService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int boundedSize = Math.max(1, Math.min(size, 100));
        PageRequest pageRequest = PageRequest.of(page, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return adminService.listUsers(role, search, pageRequest);
    }

    @GetMapping("/{id}")
    public UserResponse getOne(@PathVariable UUID id) {
        return adminService.getUser(id);
    }

    @PutMapping("/{id}/role")
    public UserResponse changeRole(@PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest request) {
        UUID currentAdminId = currentUser.getUserId();
        return adminService.changeRole(id, request.role(), currentAdminId);
    }

    @PutMapping("/{id}/unlock")
    public ResponseEntity<Void> unlock(@PathVariable UUID id) {
        adminService.unlockUser(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        UUID currentAdminId = currentUser.getUserId();
        adminService.deleteUser(id, currentAdminId);
        return ResponseEntity.noContent().build();
    }
}
