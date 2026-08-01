package io.github.ccweixiao.datastoria.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
    @NotBlank @Size(max = 64) String username,
    @NotBlank @Size(min = 8, max = 256) String password,
    @Size(max = 255) String email,
    @Pattern(regexp = "USER|ADMIN") String role) {}
