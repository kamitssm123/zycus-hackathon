package com.slotmind.example.service;

import com.slotmind.common.exception.NotFoundException;
import com.slotmind.example.api.ItemRequest;
import com.slotmind.example.api.ItemResponse;
import com.slotmind.example.domain.Item;
import com.slotmind.example.domain.ItemRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** All business rules and transaction boundaries. Controllers hold none. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemService {

  private final ItemRepository repository;

  @Transactional(readOnly = true)
  public Page<ItemResponse> list(String search, Pageable pageable) {
    Page<Item> page =
        StringUtils.hasText(search)
            ? repository.findByNameContainingIgnoreCase(search, pageable)
            : repository.findAll(pageable);
    return page.map(ItemResponse::from);
  }

  @Transactional(readOnly = true)
  public ItemResponse get(UUID id) {
    return repository
        .findById(id)
        .map(ItemResponse::from)
        .orElseThrow(() -> new NotFoundException("Item", id));
  }

  @Transactional
  public ItemResponse create(ItemRequest request) {
    Item saved =
        repository.save(
            Item.builder()
                .name(request.name())
                .description(request.description())
                .done(Boolean.TRUE.equals(request.done()))
                .build());
    log.info("created item id={}", saved.getId());
    return ItemResponse.from(saved);
  }

  @Transactional
  public ItemResponse update(UUID id, ItemRequest request) {
    Item item = repository.findById(id).orElseThrow(() -> new NotFoundException("Item", id));
    item.setName(request.name());
    item.setDescription(request.description());
    if (request.done() != null) {
      item.setDone(request.done());
    }
    return ItemResponse.from(item);
  }

  @Transactional
  public void delete(UUID id) {
    if (!repository.existsById(id)) {
      throw new NotFoundException("Item", id);
    }
    repository.deleteById(id);
  }
}
