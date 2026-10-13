package com.jobbeacon.controller;

import com.jobbeacon.dto.response.CategoryNodeResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;

    @GetMapping
    public ResponseGeneral<List<CategoryNodeResponse>> tree() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("vi"));
        collator.setStrength(Collator.PRIMARY);
        Map<UUID, MutableNode> nodes = new HashMap<>();
        List<MutableNode> roots = new ArrayList<>();

        for (Object[] row : categoryRepository.findAllForTree()) {
            MutableNode node = new MutableNode((UUID) row[0], (String) row[1], (UUID) row[2]);
            nodes.put(node.id, node);
        }
        for (MutableNode node : nodes.values()) {
            MutableNode parent = node.parentId == null ? null : nodes.get(node.parentId);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children.add(node);
            }
        }
        roots.sort(Comparator.comparing(node -> node.name, collator));
        return ResponseGeneral.ofSuccess("success", roots.stream().map(node -> node.toResponse(collator)).toList());
    }

    private static final class MutableNode {
        private final UUID id;
        private final String name;
        private final UUID parentId;
        private final List<MutableNode> children = new ArrayList<>();

        private MutableNode(UUID id, String name, UUID parentId) {
            this.id = id;
            this.name = name;
            this.parentId = parentId;
        }

        private CategoryNodeResponse toResponse(Collator collator) {
            children.sort(Comparator.comparing(node -> node.name, collator));
            return new CategoryNodeResponse(
                    id,
                    name,
                    children.stream().map(child -> child.toResponse(collator)).toList()
            );
        }
    }
}
