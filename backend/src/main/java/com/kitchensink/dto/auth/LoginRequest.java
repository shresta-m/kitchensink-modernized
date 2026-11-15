package com.kitchensink.dto.auth;



import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Username is required") @Size(max = 100, message = "Too long") String username,
        @NotBlank(message = "Password is required") @Size(max = 128, message = "Too long") String password) {}
