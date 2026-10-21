# oj-common

Code shared by the My Online Judge services.

- `event` — records published on `oj.submission.events`.
- `grpc` — the internal problem-service API (`src/main/proto`, stubs generated at build time) and the
  service-token interceptors every internal call goes through.
- `security`, `client`, `redis` — JWT verification through JWKS, `CurrentUser`, client fingerprints,
  shared Redis keys.
- `web` — what every servlet service answers with: the `ApiResponse` envelope (`payload`), the error
  infrastructure (`error`: `ErrorCodeSpec`, which each service's `ErrorCode` enum implements, the exceptions and
  the `OjExceptionHandler` base a service extends with `@RestControllerAdvice`), the JSON 401/403 handlers and
  the request log (registered by `OjWebAutoConfiguration`, servlet only).

Build with `./mvnw` (Maven 3.9.9): the protobuf plugin needs Maven ≥ 3.9.6 and runs glibc binaries
(protoc, protoc-gen-grpc-java), so Docker builds use `maven:3.9-eclipse-temurin-17`, not the Alpine image.

## 0.8.1: error codes and log levels

An `AppException` (and so a `RateLimitedException`) answer carries its `ErrorCodeSpec` name as `code`, next to
`status` and `message`:

```json
{"status":409,"message":"Slug already taken","code":"SLUG_TAKEN","timestamp":"20261021103000"}
```

Every other error (404 for an unknown path, 405, 415, validation, the catch-all 500) has no `code` key.

What `OjExceptionHandler` logs:

| Failure | Level | Stack trace |
|---|---|---|
| 4xx `AppException` | WARN, `CODE (status): message` | no |
| 5xx `AppException` | ERROR | yes |
| `RateLimitedException` 429 | DEBUG | no |
| `RateLimitedException` 5xx | WARN, `CODE (status), Retry-After Ns` | no |
| `ResourceNotFoundException` | DEBUG | no |
| `ResourceAlreadyExistException` | WARN | no |
| `IllegalArgumentException` with its own message | WARN | no |
| `IllegalArgumentException` whose message is withheld | WARN | yes |
| Validation failure (`MethodArgumentNotValidException`) | WARN, the failing field names | no |
| 403 for a signed-in caller | WARN | no |
| Catch-all 500 | ERROR | yes |
