package io.datastoria.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillResourceRequest(
    @NotBlank @Size(max = 1024) String path, @NotBlank @Size(max = 2_000_000) String content) {}
