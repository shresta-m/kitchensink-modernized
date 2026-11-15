package com.kitchensink.config;



import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Typed, validated application settings. The app refuses to start if these are invalid. */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Security security,
        @Valid @NotNull BulkImport bulkImport,
        @Valid @DefaultValue Bootstrap bootstrap) {

    public record Security(
            @NotBlank @Size(min = 32, message = "JWT secret must be at least 32 characters") String jwtSecret,
            @NotBlank String jwtIssuer,
            @NotNull Duration jwtTtl,
            boolean docsPublic,
            @NotEmpty List<String> corsAllowedOrigins,
            /** Per client address, separately for login and sign-up. */
            @DefaultValue("10") @Min(1) int authRequestsPerMinute,
            /** Secret that unlocks POST /auth/register-admin. Blank (the default) disables admin sign-up. */
            @DefaultValue("") @Pattern(regexp = "^$|.{16,}", message = "Admin sign-up code must be blank or at least 16 characters")
                    String adminSignupCode) {

        public boolean adminSignupEnabled() {
            return adminSignupCode != null && !adminSignupCode.isBlank();
        }
    }

    public record BulkImport(
            @Min(1) @Max(5000) int batchSize,
            @Min(1) @Max(32) int parallelism,
            @Min(1) @Max(100_000) int maxRows,
            @Min(1) int maxErrors) {}

    /** Optional first-run users. Blank values mean "do not seed". Passwords come from the environment only. */
    public record Bootstrap(
            String adminUsername, String adminPassword, String viewerUsername, String viewerPassword) {}
}
