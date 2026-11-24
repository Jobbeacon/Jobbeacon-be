package com.jobbeacon.dto.internal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record InternalIdsRequest(@NotNull @Size(max = 1000) List<UUID> ids) {
}
