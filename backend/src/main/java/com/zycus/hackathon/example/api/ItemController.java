package com.zycus.hackathon.example.api;

import com.zycus.hackathon.common.dto.PageResponse;
import com.zycus.hackathon.example.service.ItemService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Thin by design: validate, delegate, map status. No business logic, no try/catch. */
@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

  private final ItemService service;

  @GetMapping
  public PageResponse<ItemResponse> list(
      @RequestParam(required = false) String search,
      @PageableDefault(size = 20) Pageable pageable) {
    return PageResponse.of(service.list(search, pageable));
  }

  @GetMapping("/{id}")
  public ItemResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ItemResponse create(@Valid @RequestBody ItemRequest request) {
    return service.create(request);
  }

  @PutMapping("/{id}")
  public ItemResponse update(@PathVariable UUID id, @Valid @RequestBody ItemRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
