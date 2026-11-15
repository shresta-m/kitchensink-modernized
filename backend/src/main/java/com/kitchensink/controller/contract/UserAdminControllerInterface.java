package com.kitchensink.controller.contract;



import com.kitchensink.dto.user.CreateUserRequest;
import com.kitchensink.dto.user.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** Administrator-only account management. The ADMIN rule is enforced in SecurityConfig for /api/v1/admin/**. */
@Tag(name = "User administration")
@RequestMapping("/api/v1/admin/users")
public interface UserAdminControllerInterface {

    @Operation(summary = "Create a user with a chosen role (ADMIN or VIEWER)")
    @PostMapping
    ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest body);

    @Operation(summary = "List all users")
    @GetMapping
    List<UserResponse> list();
}
