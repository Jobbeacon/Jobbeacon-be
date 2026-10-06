package com.jobbeacon.controller;

import com.jobbeacon.dto.response.LocationItemResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.entity.Province;
import com.jobbeacon.entity.Ward;
import com.jobbeacon.repository.ProvinceRepository;
import com.jobbeacon.repository.WardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

    private static final List<String> FEATURED_PROVINCE_IDS = List.of("01", "79", "48", "31", "92");

    private final ProvinceRepository provinceRepository;
    private final WardRepository wardRepository;

    @GetMapping("/provinces")
    public ResponseGeneral<List<LocationItemResponse>> provinces() {
        Collator collator = vietnameseCollator();
        List<LocationItemResponse> provinces = provinceRepository.findAll().stream()
                .sorted(Comparator
                        .comparingInt((Province province) -> featuredRank(province.getId()))
                        .thenComparing(Province::getNameLocal, collator))
                .map(province -> new LocationItemResponse(province.getId(), province.getNameLocal()))
                .toList();
        return ResponseGeneral.ofSuccess("success", provinces);
    }

    @GetMapping("/provinces/{provinceId}/wards")
    public ResponseGeneral<List<LocationItemResponse>> wards(@PathVariable String provinceId) {
        Collator collator = vietnameseCollator();
        List<LocationItemResponse> wards = wardRepository.findByProvinceId(provinceId).stream()
                .sorted(Comparator.comparing(Ward::getNameLocal, collator))
                .map(ward -> new LocationItemResponse(ward.getId(), ward.getNameLocal()))
                .toList();
        return ResponseGeneral.ofSuccess("success", wards);
    }

    private int featuredRank(String provinceId) {
        int index = FEATURED_PROVINCE_IDS.indexOf(provinceId);
        return index < 0 ? FEATURED_PROVINCE_IDS.size() : index;
    }

    private Collator vietnameseCollator() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("vi"));
        collator.setStrength(Collator.PRIMARY);
        return collator;
    }
}
