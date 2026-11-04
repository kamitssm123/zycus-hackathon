# `example` package — reference vertical slice

This is the pattern every feature should copy: `api` (HTTP + DTOs) → `service` (business rules,
`@Transactional`) → `domain` (entity + repository). Delete this package once the real feature
exists; keep the shape.
