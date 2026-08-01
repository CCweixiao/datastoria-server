package io.github.ccweixiao.datastoria.common.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial update of a user account; all fields optional. */
public record UpdateUserRequest(
    @Pattern(regexp = "USER|ADMIN") String role,
    @Pattern(regexp = "0|1") String status,
    @Size(max = 255) String email) {}
