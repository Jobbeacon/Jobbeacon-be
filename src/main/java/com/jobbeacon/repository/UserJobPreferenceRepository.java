package com.jobbeacon.repository;

import com.jobbeacon.entity.UserJobPreference;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;
import java.util.UUID;

public interface UserJobPreferenceRepository extends BaseRepository<UserJobPreference> {

    @EntityGraph(attributePaths = "provinces")
    Optional<UserJobPreference> findByUserId(UUID userId);
}
