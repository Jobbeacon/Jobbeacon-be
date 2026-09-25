package com.jobbeacon.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jobbeacon.entity.Category;
import com.jobbeacon.repository.CategoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryEmbeddingInitializer {

    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initialize() {

        long total = categoryRepository.count();
        long embedded = categoryRepository.countByEmbeddingIsNotNull();

        log.info(
                "Category embeddings: total={}, embedded={}",
                total,
                embedded
        );

        if (total > 0 && total == embedded) {
            log.info("All categories already have embeddings. Skip.");
            return;
        }

        try {
            List<CategoryEmbeddingData> embeddingData;

            ClassPathResource resource =
                    new ClassPathResource("data/category_embeddings.json");

            try (InputStream inputStream = resource.getInputStream()) {
                embeddingData = objectMapper.readValue(
                        inputStream,
                        new TypeReference<>() {}
                );
            }

            List<Category> categories = categoryRepository.findAll();

            Map<String, Category> categoryByPath = categories.stream()
                    .collect(Collectors.toMap(
                            Category::getPath,
                            Function.identity()
                    ));

            int updated = 0;
            int skipped = 0;

            for (CategoryEmbeddingData data : embeddingData) {

                if (data.pathText() == null || data.embedding() == null) {
                    log.warn(
                            "Invalid embedding data: {}",
                            data.name()
                    );
                    skipped++;
                    continue;
                }

                Category category = categoryByPath.get(data.pathText());

                if (category == null) {
                    log.warn(
                            "Category not found: {}",
                            data.pathText()
                    );
                    skipped++;
                    continue;
                }

                if (category.getEmbedding() != null) {
                    skipped++;
                    continue;
                }

                category.setEmbedding(data.embedding());
                category.setEmbeddingModel(data.embeddingModel());
                category.setEmbeddingDimension(data.embeddingDimension());

                updated++;
            }

            categoryRepository.flush();

            log.info(
                    "Category embedding initialization completed. updated={}, skipped={}",
                    updated,
                    skipped
            );

        } catch (Exception e) {
            log.error("Failed to initialize category embeddings", e);

            throw new IllegalStateException(
                    "Failed to initialize category embeddings",
                    e
            );
        }
    }

    private record CategoryEmbeddingData(
            String name,

            Integer level,

            List<String> path,

            @JsonProperty("path_text")
            String pathText,

            @JsonProperty("is_leaf")
            Boolean isLeaf,

            float[] embedding,

            @JsonProperty("embedding_model")
            String embeddingModel,

            @JsonProperty("embedding_dimension")
            Integer embeddingDimension
    ) {
    }
}