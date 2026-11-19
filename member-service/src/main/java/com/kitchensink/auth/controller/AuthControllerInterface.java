package com.kitchensink.auth.controller;

import com.kitchensink.auth.dto.ChangePasswordRequest;
import com.kitchensink.auth.dto.LoginRequest;
import com.kitchensink.auth.dto.LoginResponse;
import com.kitchensink.auth.dto.RegisterRequest;
import com.kitchensink.auth.dto.RegisterResponse;
import com.kitchensink.common.dto.ApiError;
import com.kitchensink.member.dto.MemberResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.ResponseEntity;

/** The auth API contract and its OpenAPI documentation. {@link AuthController} implements it. */
@Tag(name = "Auth", description = "Register, sign in, current member and password change")
public interface AuthControllerInterface {

    @Operation(summary = "Register a member",
            description = "No admin code (null, empty or missing) creates a VIEWER. A valid admin code creates an ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Member created"),
            @ApiResponse(responseCode = "400", description = "Validation failed or admin code not valid",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Too many wrong admin codes from this address, or overall. See Retry-After.",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    ResponseEntity<RegisterResponse> register(@Valid RegisterRequest request, HttpServletRequest http);

    @Operation(summary = "Sign in",
            description = "Returns a bearer token valid for 30 minutes. If mustChangePassword is true, only /auth/me and "
                    + "/auth/change-password work until the password is changed.")
    @ApiResponse(responseCode = "200", description = "Signed in")
    @ApiResponse(responseCode = "401", description = "Invalid email or password",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "429", description = "Too many failed attempts for this email or address. See Retry-After.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    LoginResponse login(@Valid LoginRequest request, HttpServletRequest http);

    @Operation(summary = "Sign out everywhere", security = @SecurityRequirement(name = "bearerAuth"),
            description = "Invalidates every token issued to this member so far, not just the one used for this call.")
    @ApiResponse(responseCode = "204", description = "Signed out")
    ResponseEntity<Void> logout(Principal principal);

    @Operation(summary = "The signed-in member", security = @SecurityRequirement(name = "bearerAuth"))
    MemberResponse me(Principal principal);

    @Operation(summary = "Change my password", security = @SecurityRequirement(name = "bearerAuth"),
            description = "Signs the member out everywhere: every token, including this one, stops working, so sign in again.")
    @ApiResponse(responseCode = "204", description = "Password changed")
    @ApiResponse(responseCode = "400", description = "Current password wrong, new password weak or unchanged",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "429", description = "Too many wrong current passwords. See Retry-After.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    ResponseEntity<Void> changePassword(Principal principal, @Valid ChangePasswordRequest request);
}
