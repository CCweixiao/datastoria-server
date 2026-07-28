package io.github.ccweixiao.datastoria.common.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpsertSkillRequest(
    @Pattern(regexp = "[a-z][a-z0-9_-]{0,254}") String id,
    @Size(max = 2_000_000) String content,
    @Pattern(regexp = "global|self") String scope,
    @Size(max = 128) String version,
    @Valid List<SkillResourceRequest> resources,
    List<@Size(max = 440) String> deletedResourcePaths,
    @Pattern(regexp = "draft|published") String state,
    @Pattern(regexp = "publish") String action) {}
