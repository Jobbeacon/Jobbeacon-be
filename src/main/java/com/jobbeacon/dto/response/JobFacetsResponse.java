package com.jobbeacon.dto.response;

import java.util.List;

public record JobFacetsResponse(List<FacetItemResponse> categories, List<FacetItemResponse> skills) {
}
