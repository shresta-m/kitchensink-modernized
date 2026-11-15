package com.kitchensink.controller.contract;



import com.kitchensink.dto.auth.AdminRegisterRequest;
import com.kitchensink.dto.auth.LoginRequest;
import com.kitchensink.dto.auth.LoginResponse;
import com.kitchensink.dto.auth.RegisterRequest;
import com.kitchensink.dto.user.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Auth")
@RequestMapping("/api/v1/auth")
public interface AuthControllerInterface {

    @Operation(summary = "Log in and receive a bearer token")
    @SecurityRequirements
    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest body);

    @Operation(summary = "Create a read-only (VIEWER) account and sign in",
            description = "Self-service sign-up. The role cannot be chosen here: it is always VIEWER. "
                    + "Administrators are created by an existing admin through /api/v1/admin/users.")
    @SecurityRequirements
    @PostMapping("/register")
    ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest body);

    @Operation(summary = "Create an ADMIN account with the server's admin sign-up code",
            description = "Separate from public sign-up. Returns 404 unless app.security.admin-signup-code is "
                    + "configured, 403 for a wrong code. Does not sign in: the client sends the admin to the login page.")
    @SecurityRequirements
    @PostMapping("/register-admin")
    ResponseEntity<UserResponse> registerAdmin(@Valid @RequestBody AdminRegisterRequest body);

    @Operation(summary = "The current user")
    @GetMapping("/me")
    LoginResponse.UserInfo me(@AuthenticationPrincipal Jwt jwt);
}
