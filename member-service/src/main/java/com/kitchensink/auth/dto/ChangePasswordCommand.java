package com.kitchensink.auth.dto;

public record ChangePasswordCommand(String currentPassword, String newPassword) {
}
