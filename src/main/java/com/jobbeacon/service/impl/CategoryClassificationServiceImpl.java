package com.jobbeacon.service.impl;

import com.jobbeacon.client.GeminiEmbeddingClient;
import com.jobbeacon.dto.message.JobPostingMessage;
import com.jobbeacon.entity.Category;
import com.jobbeacon.repository.CategoryRepository;
import com.jobbeacon.repository.projection.CategoryDistance;
import com.jobbeacon.service.CategoryClassificationService;
import com.jobbeacon.utils.HtmlTextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CategoryClassificationServiceImpl implements CategoryClassificationService {

    private static final String TASK_TYPE = "CLASSIFICATION";

    private static final int MAX_LEVEL_SAFETY_CAP = 20;

    private final GeminiEmbeddingClient geminiEmbeddingClient;
    private final CategoryRepository categoryRepository;

    @Value("${app.category-classification.min-score}")
    private double minScore;

    @Value("${app.category-classification.min-margin}")
    private double minMargin;

    public CategoryClassificationServiceImpl(GeminiEmbeddingClient geminiEmbeddingClient,
                                              CategoryRepository categoryRepository) {
        this.geminiEmbeddingClient = geminiEmbeddingClient;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public Optional<Category> classifyDeepestCategory(JobPostingMessage message) {
        String embeddingText = buildEmbeddingText(message);
        if (embeddingText.isBlank()) {
            log.warn("Skip category classification: empty embedding text for job '{}'", message.getTitle());
            return Optional.empty();
        }

        float[] jobEmbedding;
        try {
            jobEmbedding = geminiEmbeddingClient.embed(embeddingText, TASK_TYPE);
        } catch (Exception e) {
            log.error("Failed to generate job embedding for '{}'. Skip category classification.", message.getTitle(), e);
            return Optional.empty();
        }

        Category selected = null;
        UUID parentId = null;
        Pageable top2 = PageRequest.of(0, 2);

        for (int level = 1; level <= MAX_LEVEL_SAFETY_CAP; level++) {
            List<CategoryDistance> ranked = (parentId == null)
                    ? categoryRepository.findNearestRootCategories(level, jobEmbedding, top2)
                    : categoryRepository.findNearestChildCategories(level, parentId, jobEmbedding, top2);

            if (ranked.isEmpty()) {
                break;
            }

            CategoryDistance top1 = ranked.get(0);
            double top1Score = 1.0 - top1.distance();
            double top2Score = ranked.size() > 1 ? 1.0 - ranked.get(1).distance() : Double.NEGATIVE_INFINITY;
            double margin = top1Score - top2Score;

            log.debug(
                    "[classifyDeepestCategory] level={} top1='{}' score={} margin={}",
                    level, top1.category().getPath(), top1Score, margin
            );

            if (top1Score < minScore || margin < minMargin) {
                log.debug(
                        "[classifyDeepestCategory] stop at level={} (score>=min:{}, margin>=min:{})",
                        level, top1Score >= minScore, margin >= minMargin
                );
                break;
            }

            selected = top1.category();
            parentId = selected.getId();
        }

        return Optional.ofNullable(selected);
    }

    private String buildEmbeddingText(JobPostingMessage message) {
        List<String> parts = new ArrayList<>();

        String title = message.getTitle();
        if (title != null && !title.isBlank()) {
            parts.add("Job title:\n" + title.trim());
        }

        List<String> skills = message.getSkills();
        if (skills != null && !skills.isEmpty()) {
            String skillText = skills.stream()
                    .filter(skill -> skill != null && !skill.isBlank())
                    .map(String::trim)
                    .collect(Collectors.joining(", "));
            if (!skillText.isBlank()) {
                parts.add("Technical skills:\n" + skillText);
            }
        }

        String industry = message.getIndustry();
        if (industry != null && !industry.isBlank()) {
            String industryText = Arrays.stream(industry.split(","))
                    .map(String::trim)
                    .filter(name -> !name.isBlank())
                    .collect(Collectors.joining(", "));
            if (!industryText.isBlank()) {
                parts.add("Industry:\n" + industryText);
            }
        }

        String descriptionText = HtmlTextUtils.toPlainText(message.getDescription());
        if (!descriptionText.isBlank()) {
            parts.add("Job description:\n" + descriptionText);
        }

        return String.join("\n\n", parts);
    }
}
