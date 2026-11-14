package com.jobbeacon.controller;

import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.dto.response.SkillSearchItemResponse;
import com.jobbeacon.service.SkillLookupService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
public class SkillController {

  private final SkillLookupService skillLookupService;

  @GetMapping
  public ResponseGeneral<List<SkillSearchItemResponse>> search(
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "10") int limit
  ) {
    return ResponseGeneral.ofSuccess("success",
        skillLookupService.search(q, limit).stream().map(SkillSearchItemResponse::from).toList());
  }
}
