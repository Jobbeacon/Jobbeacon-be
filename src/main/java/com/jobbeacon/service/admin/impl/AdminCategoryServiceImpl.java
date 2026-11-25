package com.jobbeacon.service.admin.impl;

import com.jobbeacon.client.GeminiEmbeddingClient;
import com.jobbeacon.dto.request.admin.AdminCategoryRequest;
import com.jobbeacon.dto.response.admin.AdminCategoryNodeResponse;
import com.jobbeacon.entity.Category;
import com.jobbeacon.exception.base.BadRequestException;
import com.jobbeacon.exception.base.ConflictException;
import com.jobbeacon.exception.base.NotFoundException;
import com.jobbeacon.repository.CategoryRepository;
import com.jobbeacon.repository.admin.AdminCategoryQueryRepository;
import com.jobbeacon.search.JobSearchReindexer;
import com.jobbeacon.service.admin.AdminCategoryService;
import com.jobbeacon.utils.CompanyNameUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCategoryServiceImpl implements AdminCategoryService {

  static final int MAX_LEVEL = 3;
  private static final String PATH_SEPARATOR = " > ";
  private static final String CATEGORY_NOT_FOUND = "category_not_found";
  /** Same task type the job side uses when it is matched against category embeddings. */
  private static final String EMBEDDING_TASK_TYPE = "CLASSIFICATION";

  private final CategoryRepository categoryRepository;
  private final AdminCategoryQueryRepository queryRepository;
  private final GeminiEmbeddingClient geminiEmbeddingClient;
  private final JobSearchReindexer jobSearchReindexer;

  @Value("${app.gemini.embedding-model:gemini-embedding-2}")
  private String embeddingModel;

  @Override
  @Transactional(readOnly = true)
  public List<AdminCategoryNodeResponse> tree() {
    Map<UUID, Long> counts = queryRepository.jobCountsWithDescendants();
    Map<UUID, Node> nodes = new HashMap<>();
    for (Object[] row : categoryRepository.findAllForTree()) {
      Node node = new Node((UUID) row[0], (String) row[1], (UUID) row[2], (Integer) row[3]);
      nodes.put(node.id, node);
    }
    List<Node> roots = new ArrayList<>();
    for (Node node : nodes.values()) {
      Node parent = node.parentId == null ? null : nodes.get(node.parentId);
      if (parent == null) {
        roots.add(node);
      } else {
        parent.children.add(node);
      }
    }
    Collator collator = Collator.getInstance(Locale.forLanguageTag("vi"));
    collator.setStrength(Collator.PRIMARY);
    return sorted(roots, collator).stream().map(root -> root.toResponse(1, counts, collator)).toList();
  }

  @Override
  @Transactional
  public AdminCategoryNodeResponse create(AdminCategoryRequest request) {
    String name = request.getName().trim();
    Category parent = null;
    int level = 1;
    if (request.getParentId() != null) {
      parent = categoryRepository.findById(request.getParentId())
          .orElseThrow(() -> new NotFoundException(CATEGORY_NOT_FOUND));
      level = levelOf(parent) + 1;
      if (level > MAX_LEVEL) {
        throw new BadRequestException("admin_category_max_depth");
      }
    }
    ensureUniqueAmongSiblings(parent, name, null);
    Category category = new Category();
    category.setName(name);
    category.setParent(parent);
    category.setLevel(level);
    category.setPath(parent == null ? name : pathOf(parent) + PATH_SEPARATOR + name);
    embed(category);
    category = categoryRepository.save(category);
    log.info("(admin create category) id={} path={}", category.getId(), category.getPath());
    return new AdminCategoryNodeResponse(category.getId(), name, level, 0, List.of());
  }

  @Override
  @Transactional
  public AdminCategoryNodeResponse rename(UUID id, AdminCategoryRequest request) {
    Category category = categoryRepository.findById(id).orElseThrow(() -> new NotFoundException(CATEGORY_NOT_FOUND));
    String name = request.getName().trim();
    ensureUniqueAmongSiblings(category.getParent(), name, id);
    if (!name.equals(category.getName())) {
      String oldPath = pathOf(category);
      category.setName(name);
      String newPath = category.getParent() == null ? name : pathOf(category.getParent()) + PATH_SEPARATOR + name;
      category.setPath(newPath);
      embed(category);
      renameDescendantPaths(category, oldPath, newPath);
      categoryRepository.save(category);
      log.info("(admin rename category) id={} path={}", id, newPath);
      // the search document stores category names of each job
      jobSearchReindexer.reindex(queryRepository.jobIds(id));
    }
    return tree().stream()
        .map(root -> find(root, id))
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElseThrow(() -> new NotFoundException(CATEGORY_NOT_FOUND));
  }

  @Override
  @Transactional
  public void delete(UUID id) {
    Category category = categoryRepository.findById(id).orElseThrow(() -> new NotFoundException(CATEGORY_NOT_FOUND));
    if (categoryRepository.existsByParentId(id)) {
      throw new ConflictException("admin_category_has_children");
    }
    List<UUID> jobIds = queryRepository.jobIds(id);
    queryRepository.deleteJobMappings(id);
    categoryRepository.delete(category);
    log.info("(admin delete category) id={} jobs={}", id, jobIds.size());
    jobSearchReindexer.reindex(jobIds);
  }

  private void ensureUniqueAmongSiblings(Category parent, String name, UUID exceptId) {
    String key = CompanyNameUtils.fold(name);
    List<Category> siblings = parent == null ? categoryRepository.findRoots() : categoryRepository.findChildren(parent.getId());
    boolean clash = siblings.stream()
        .filter(sibling -> !sibling.getId().equals(exceptId))
        .anyMatch(sibling -> CompanyNameUtils.fold(sibling.getName()).equals(key));
    if (clash) {
      throw new ConflictException("admin_category_name_exists");
    }
  }

  private void renameDescendantPaths(Category category, String oldPath, String newPath) {
    for (Category child : categoryRepository.findChildren(category.getId())) {
      String childOld = pathOf(child);
      String childNew = childOld.startsWith(oldPath + PATH_SEPARATOR)
          ? newPath + childOld.substring(oldPath.length())
          : newPath + PATH_SEPARATOR + child.getName();
      child.setPath(childNew);
      renameDescendantPaths(child, childOld, childNew);
    }
  }

  /** Embedding used by the crawler's classifier; left empty when the embedding service is unavailable. */
  private void embed(Category category) {
    try {
      category.setEmbedding(geminiEmbeddingClient.embed(category.getPath(), EMBEDDING_TASK_TYPE));
      category.setEmbeddingModel(embeddingModel);
      category.setEmbeddingDimension(category.getEmbedding() == null ? null : category.getEmbedding().length);
    } catch (Exception e) {
      log.warn("(admin category embedding) skipped for '{}': {}", category.getPath(), e.getMessage());
      category.setEmbedding(null);
      category.setEmbeddingModel(null);
      category.setEmbeddingDimension(null);
    }
  }

  private static String pathOf(Category category) {
    if (category.getPath() != null && !category.getPath().isBlank()) {
      return category.getPath();
    }
    List<String> names = new ArrayList<>();
    for (Category node = category; node != null && names.size() < 10; node = node.getParent()) {
      names.add(0, node.getName());
    }
    return String.join(PATH_SEPARATOR, names);
  }

  private static int levelOf(Category category) {
    if (category.getLevel() != null) {
      return category.getLevel();
    }
    int level = 0;
    for (Category node = category; node != null && level < 10; node = node.getParent()) {
      level++;
    }
    return level;
  }

  private static AdminCategoryNodeResponse find(AdminCategoryNodeResponse node, UUID id) {
    if (node.id().equals(id)) {
      return node;
    }
    for (AdminCategoryNodeResponse child : node.children()) {
      AdminCategoryNodeResponse found = find(child, id);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private static List<Node> sorted(List<Node> nodes, Collator collator) {
    return nodes.stream()
        .sorted(Comparator.comparing((Node node) -> node.name == null ? "" : node.name, collator))
        .toList();
  }

  private static final class Node {
    private final UUID id;
    private final String name;
    private final UUID parentId;
    private final Integer level;
    private final List<Node> children = new ArrayList<>();

    private Node(UUID id, String name, UUID parentId, Integer level) {
      this.id = id;
      this.name = name;
      this.parentId = parentId;
      this.level = level;
    }

    private AdminCategoryNodeResponse toResponse(int depth, Map<UUID, Long> counts, Collator collator) {
      return new AdminCategoryNodeResponse(
          id,
          name,
          level == null ? depth : level,
          counts.getOrDefault(id, 0L),
          sorted(children, collator).stream().map(child -> child.toResponse(depth + 1, counts, collator)).toList()
      );
    }
  }
}
