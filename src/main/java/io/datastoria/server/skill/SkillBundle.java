package io.datastoria.server.skill;

import java.util.List;
import java.util.Map;

/** Immutable, validated representation of one classpath Skill bundle. */
public record SkillBundle(
    String id,
    String name,
    String description,
    String version,
    String skillMarkdown,
    Map<String, String> metadata,
    List<String> requiredTools,
    Map<String, String> resources,
    String checksum) {}
