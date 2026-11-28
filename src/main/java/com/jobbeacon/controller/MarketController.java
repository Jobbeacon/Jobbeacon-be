package com.jobbeacon.controller;

import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.dto.response.market.MarketOverviewResponse;
import com.jobbeacon.service.MarketInsightService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public market statistics; listed in the gateway's public GET paths. */
@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class MarketController {

    private final MarketInsightService marketInsightService;

    @GetMapping("/overview")
    public ResponseGeneral<MarketOverviewResponse> overview(
            @RequestParam(name = "category_id", required = false) UUID categoryId,
            @RequestParam(name = "province_id", required = false) String provinceId,
            @RequestParam(name = "skill_id", required = false) UUID skillId
    ) {
        return ResponseGeneral.ofSuccess("success", marketInsightService.overview(categoryId, provinceId, skillId));
    }
}
