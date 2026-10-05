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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class SkillResolutionServiceImpl implements SkillResolutionService {

    public static final String EMBEDDING_TASK_TYPE = "SEMANTIC_SIMILARITY";

    private static final String MATCH_INDEX_FIELD = "match_index";

    private static final String CLASSIFICATION_SYSTEM_PROMPT = """
            You are deduplicating the skill taxonomy of a Vietnamese job platform.
            Skills can be technical (programming languages, frameworks, tools), professional
            (accounting, sales, design, ...) or soft skills, written in Vietnamese or English.

            Task: decide whether the NEW SKILL is exactly the same skill as one of the EXISTING SKILLS,
            only written differently.

            Count as the SAME skill (match):
            - Spelling, spacing, punctuation or suffix variants:
              "ReactJS" = "React.js" = "React", "NodeJS" = "Node.js", "Golang" = "Go", "Team work" = "Teamwork".
            - Abbreviation vs full name: "JS" = "JavaScript", "K8s" = "Kubernetes", "UI/UX" = "UI/UX Design".
            - Version-less name: "Python 3" = "Python", "Angular 17" = "Angular".
            - Vietnamese vs English translation of the same skill:
              "làm việc nhóm" = "teamwork", "giao tiếp" = "communication", "kế toán" = "accounting",
              "tiếng Anh" = "English".
            - Vietnamese wording variants with the same meaning, including missing diacritics:
              "làm việc nhóm" = "làm việc theo nhóm" = "kỹ năng làm việc nhóm" = "lam viec nhom",
              "giải quyết vấn đề" = "kỹ năng giải quyết vấn đề",
              "tin học văn phòng" = "MS Office".
              Generic prefixes such as "kỹ năng", "khả năng", "skill", "kinh nghiệm" do not change the skill.

            NOT the same skill (no match):
            - Related or overlapping technologies: "Java" vs "JavaScript", "React" vs "React Native",
              "SQL" vs "MySQL", "AWS" vs "AWS Lambda", "Spring" vs "Spring Boot".
            - A broader or narrower skill: "Marketing" vs "Digital Marketing", "Excel" vs "tin học văn phòng",
              "tiếng Anh" vs "tiếng Anh giao tiếp", "làm việc nhóm" vs "quản lý nhóm".
            - Opposite or different soft skills: "làm việc nhóm" vs "làm việc độc lập",
              "giao tiếp" vs "thuyết trình", "chịu được áp lực" vs "quản lý thời gian".
            - Skills that share words but differ in meaning: "kế toán thuế" vs "kế toán tổng hợp",
              "thiết kế đồ họa" vs "thiết kế nội thất".

            When unsure, do NOT match: a wrong merge is worse than a duplicate.
            If several existing skills match, choose the one listed first.

            Input: a NEW SKILL and a numbered list of EXISTING SKILLS.
            Respond with JSON only: {"match_index": <number of the matched existing skill, or 0 if none>}
            """;

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
        String response;
        try {
            response = geminiChatClient.generateJson(CLASSIFICATION_SYSTEM_PROMPT, prompt);
        } catch (Exception e) {
            log.error("[classifyWithLlm] Failed to call LLM for skill '{}'. Treat as new skill.", name, e);
            return Optional.empty();
        }
        log.info("[classifyWithLlm] LLM response for '{}': {}", name, response);

        JsonNode matchIndex;
        try {
            matchIndex = objectMapper.readTree(response).path(MATCH_INDEX_FIELD);
        } catch (Exception e) {
            log.warn("[classifyWithLlm] LLM response for '{}' is not valid JSON: {}", name, response);
            return Optional.empty();
        }

        if (!matchIndex.canConvertToInt()) {
            log.warn("[classifyWithLlm] Invalid LLM response for '{}': {}", name, response);
            return Optional.empty();
        }

        int index = matchIndex.asInt();
        if (index < 0 || index > candidates.size()) {
            log.warn("[classifyWithLlm] LLM returned out-of-range match_index {} for '{}' ({} candidates)",
                    index, name, candidates.size());
            return Optional.empty();
        }
        if (index == 0) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(index - 1).skill());
    }

    private String buildClassificationPrompt(String name, List<SkillDistance> candidates) {
        StringBuilder candidateText = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            candidateText.append(i + 1).append(". ").append(candidates.get(i).skill().getName()).append('\n');
        }

        return """
                NEW SKILL: %s

                EXISTING SKILLS:
                %s""".formatted(name, candidateText);
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
