package io.github.ccweixiao.datastoria.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 256) String password) {}
