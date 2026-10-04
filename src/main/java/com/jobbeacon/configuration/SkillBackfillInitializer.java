package com.jobbeacon.configuration;

import com.jobbeacon.client.GeminiEmbeddingClient;
import com.jobbeacon.entity.Skill;
import com.jobbeacon.repository.SkillRepository;
import com.jobbeacon.service.impl.SkillResolutionServiceImpl;
import com.jobbeacon.utils.SkillUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fills normalized_name and embedding for skills created before the skill_alias migration,
 * so they can be matched by the skill resolution flow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillBackfillInitializer {

    private final SkillRepository skillRepository;
    private final GeminiEmbeddingClient geminiEmbeddingClient;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        backfillNormalizedNames();
        backfillEmbeddings();
    }

    private void backfillNormalizedNames() {
        List<Skill> skills = skillRepository.findAllByNormalizedNameIsNull();
        if (skills.isEmpty()) {
            return;
        }

        Set<String> assigned = new HashSet<>();
        int updated = 0;
        int skipped = 0;
        for (Skill skill : skills) {
            String normalized = SkillUtils.normalizeSkill(skill.getName());
            if (normalized == null || normalized.isEmpty()
                    || assigned.contains(normalized)
                    || skillRepository.existsByNormalizedName(normalized)) {
                log.warn("Skip normalizing skill '{}' (id={}): empty or duplicated normalized name",
                        skill.getName(), skill.getId());
                skipped++;
                continue;
            }

            skill.setNormalizedName(normalized);
            skillRepository.save(skill);
            assigned.add(normalized);
            updated++;
        }

        log.info("Skill normalized_name backfill completed. updated={}, skipped={}", updated, skipped);
    }

    private void backfillEmbeddings() {
        List<Skill> skills = skillRepository.findAllByEmbeddingIsNull();
        if (skills.isEmpty()) {
            return;
        }

        int updated = 0;
        int failed = 0;
        for (Skill skill : skills) {
            try {
                skill.setEmbedding(geminiEmbeddingClient.embed(
                        skill.getName(), SkillResolutionServiceImpl.EMBEDDING_TASK_TYPE));
                skillRepository.save(skill);
                updated++;
            } catch (Exception e) {
                log.warn("Failed to embed skill '{}' (id={}): {}", skill.getName(), skill.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("Skill embedding backfill completed. updated={}, failed={}", updated, failed);
    }
}
