package lk.swiftdrop.identity.controller;

import java.util.UUID;
import lk.swiftdrop.identity.dto.UserResponse;
import lk.swiftdrop.identity.service.AdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final AdminService adminService;

    public InternalUserController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable UUID id) {
        return adminService.getUser(id);
    }
}
