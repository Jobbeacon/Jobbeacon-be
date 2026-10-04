package com.jobbeacon.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbeacon.client.GeminiChatClient;
import com.jobbeacon.client.GeminiEmbeddingClient;
import com.jobbeacon.entity.Skill;
import com.jobbeacon.entity.SkillAlias;
import com.jobbeacon.repository.SkillAliasRepository;
import com.jobbeacon.repository.SkillRepository;
import com.jobbeacon.repository.projection.SkillDistance;
import com.jobbeacon.service.SkillResolutionService;
import com.jobbeacon.utils.SkillUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class SkillResolutionServiceImpl implements SkillResolutionService {

    public static final String EMBEDDING_TASK_TYPE = "SEMANTIC_SIMILARITY";

    private static final String MATCH_INDEX_FIELD = "match_index";

    private final SkillRepository skillRepository;
    private final SkillAliasRepository skillAliasRepository;
    private final GeminiEmbeddingClient geminiEmbeddingClient;
    private final GeminiChatClient geminiChatClient;
    private final ObjectMapper objectMapper;

    @Value("${app.skill-resolution.min-similarity:0.7}")
    private double minSimilarity;

    @Value("${app.skill-resolution.top-k:10}")
    private int topK;

    public SkillResolutionServiceImpl(SkillRepository skillRepository,
                                      SkillAliasRepository skillAliasRepository,
                                      GeminiEmbeddingClient geminiEmbeddingClient,
                                      GeminiChatClient geminiChatClient,
                                      ObjectMapper objectMapper) {
        this.skillRepository = skillRepository;
        this.skillAliasRepository = skillAliasRepository;
        this.geminiEmbeddingClient = geminiEmbeddingClient;
        this.geminiChatClient = geminiChatClient;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public Set<Skill> resolveSkills(List<String> rawSkills) {
        if (rawSkills == null || rawSkills.isEmpty()) {
            return new HashSet<>();
        }

        Map<String, Skill> resolved = new LinkedHashMap<>();
        for (String rawSkill : rawSkills) {
            String normalized = SkillUtils.normalizeSkill(rawSkill);
            if (normalized == null || normalized.isEmpty() || resolved.containsKey(normalized)) {
                continue;
            }
            resolved.put(normalized, resolveSkill(rawSkill.trim(), normalized));
        }

        return new HashSet<>(resolved.values());
    }

    private Skill resolveSkill(String name, String normalized) {
        Optional<Skill> existing = findByNormalized(normalized);
        if (existing.isPresent()) {
            return existing.get();
        }

        float[] embedding;
        try {
            embedding = geminiEmbeddingClient.embed(name, EMBEDDING_TASK_TYPE);
        } catch (Exception e) {
            log.error("[resolveSkill] Failed to embed skill '{}'. Create skill without embedding.", name, e);
            return createSkill(name, normalized, null);
        }

        List<SkillDistance> candidates = skillRepository.findNearestSkills(
                embedding, 1.0 - minSimilarity, PageRequest.of(0, topK));
        if (candidates.isEmpty()) {
            log.debug("[resolveSkill] No candidate for '{}'. Create new skill.", name);
            return createSkill(name, normalized, embedding);
        }

        Optional<Skill> matched = classifyWithLlm(name, candidates);
        if (matched.isPresent()) {
            Skill skill = matched.get();
            log.info("[resolveSkill] '{}' classified as alias of '{}'", name, skill.getName());
            createAlias(name, normalized, skill);
            return skill;
        }

        log.info("[resolveSkill] '{}' classified as new skill", name);
        return createSkill(name, normalized, embedding);
    }

    private Optional<Skill> findByNormalized(String normalized) {
        return skillRepository.findByNormalizedName(normalized)
                .or(() -> skillAliasRepository.findByNormalizedAlias(normalized).map(SkillAlias::getSkill));
    }

    private Optional<Skill> classifyWithLlm(String name, List<SkillDistance> candidates) {
        String prompt = buildClassificationPrompt(name, candidates);
        try {
            String response = geminiChatClient.generateJson(prompt);
            JsonNode matchIndex = objectMapper.readTree(response).path(MATCH_INDEX_FIELD);
            if (!matchIndex.canConvertToInt()) {
                log.warn("[classifyWithLlm] Invalid LLM response for '{}': {}", name, response);
                return Optional.empty();
            }

            int index = matchIndex.asInt();
            if (index < 1 || index > candidates.size()) {
                return Optional.empty();
            }
            return Optional.of(candidates.get(index - 1).skill());
        } catch (Exception e) {
            log.error("[classifyWithLlm] Failed to classify skill '{}'. Treat as new skill.", name, e);
            return Optional.empty();
        }
    }

    private String buildClassificationPrompt(String name, List<SkillDistance> candidates) {
        StringBuilder candidateText = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            SkillDistance candidate = candidates.get(i);
            candidateText.append(String.format(Locale.ROOT, "%d. %s (similarity: %.3f)%n",
                    i + 1, candidate.skill().getName(), 1.0 - candidate.distance()));
        }

        return """
                You are deduplicating a skill taxonomy for a job platform.
                Decide whether the NEW SKILL is the same skill as one of the EXISTING SKILLS,
                only written differently (abbreviation, alternative spelling, version-less name,
                translation, suffix like "JS"/".js"/"framework", ...).

                Rules:
                - Only match when both names refer to exactly the same skill/technology.
                - Related but different skills are NOT a match (e.g. "Java" vs "JavaScript",
                  "React" vs "React Native", "SQL" vs "MySQL", "AWS" vs "AWS Lambda").
                - If there is no exact match, return 0.

                NEW SKILL: %s

                EXISTING SKILLS:
                %s
                Respond with JSON only: {"match_index": <number of the matched existing skill, or 0>}
                """.formatted(name, candidateText);
    }

    private Skill createSkill(String name, String normalized, float[] embedding) {
        Skill skill = new Skill();
        skill.setName(name);
        skill.setNormalizedName(normalized);
        skill.setEmbedding(embedding);
        return skillRepository.save(skill);
    }

    private void createAlias(String alias, String normalized, Skill skill) {
        SkillAlias skillAlias = new SkillAlias();
        skillAlias.setAlias(alias);
        skillAlias.setNormalizedAlias(normalized);
        skillAlias.setSkill(skill);
        skillAliasRepository.save(skillAlias);
    }
}
