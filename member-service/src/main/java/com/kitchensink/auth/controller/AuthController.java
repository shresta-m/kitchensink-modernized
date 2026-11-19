package com.kitchensink.auth.controller;

import com.kitchensink.auth.dto.ChangePasswordRequest;
import com.kitchensink.auth.dto.LoginRequest;
import com.kitchensink.auth.dto.LoginResponse;
import com.kitchensink.auth.dto.RegisterRequest;
import com.kitchensink.auth.dto.RegisterResponse;
import com.kitchensink.auth.service.AuthService;
import com.kitchensink.auth.dto.ChangePasswordCommand;
import com.kitchensink.auth.dto.LoginCommand;
import com.kitchensink.auth.dto.LoginResult;
import com.kitchensink.auth.dto.RegisterCommand;
import com.kitchensink.member.dto.MemberResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Routing, validation and DTO mapping for auth. The use cases live in {@link AuthService}. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerInterface {

    private final AuthService authService;

    @Override
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request,
                                                     HttpServletRequest http) {
        RegisterCommand command = new RegisterCommand(request.toDetails(), request.getPassword(),
                request.getAdminCode(), http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterResponse.from(authService.register(command)));
    }

    @Override
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        LoginResult result = authService.login(
                new LoginCommand(request.getEmail(), request.getPassword(), http.getRemoteAddr()));
        return LoginResponse.of(result.member(), result.accessToken(), result.expiresInSeconds());
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Principal principal) {
        authService.logout(principal.getName());
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/me")
    public MemberResponse me(Principal principal) {
        return MemberResponse.from(authService.me(principal.getName()));
    }

    @Override
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(Principal principal, @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getName(),
                new ChangePasswordCommand(request.getCurrentPassword(), request.getNewPassword()));
        return ResponseEntity.noContent().build();
    }
}
