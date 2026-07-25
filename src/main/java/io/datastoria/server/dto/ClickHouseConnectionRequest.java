package io.datastoria.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClickHouseConnectionRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = 2048) String url,
    @NotBlank @Size(max = 255) String username,
    @Size(max = 255) String password,
    @Size(max = 255) String cluster,
    Boolean enabled) {}
