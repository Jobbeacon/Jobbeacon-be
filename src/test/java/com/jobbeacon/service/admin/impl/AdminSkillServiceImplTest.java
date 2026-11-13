package com.jobbeacon.service.admin.impl;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminSkillServiceImplTest {

  @Test
  void cleanAliasesDropsBlanksDuplicatesAndOwnName() {
    Map<String, String> aliases = AdminSkillServiceImpl.cleanAliases(
        Arrays.asList(" JS ", "js", "", null, "JavaScript", "Node  JS"), "javascript");

    assertEquals(List.of("js", "node js"), List.copyOf(aliases.keySet()));
    assertEquals("JS", aliases.get("js"));
    assertEquals("Node JS", aliases.get("node js"));
  }

  @Test
  void cleanAliasesAcceptsNull() {
    assertTrue(AdminSkillServiceImpl.cleanAliases(null, "java").isEmpty());
  }
}
