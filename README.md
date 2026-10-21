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

## 0.8.1: `PublicUserClient` misses and backoff

`PublicUserClient` (`identity`, wired when `oj.identity.base-url` is set) caches absences as well as profiles:

- A 404 from `byUsername`, or an id missing from identity's answer to `byIds`, is remembered as absent for
  `oj.identity.negative-cache-ttl` (default `60s`) and not asked again. A profile that turns up later replaces the
  absence. Profiles keep `oj.identity.cache-ttl` (default `5m`). Both share the 10 000-entry cap per map.
- After a failed call (any error but a 404) the client sends no request for 1 s, then 2 s, 4 s ... up to 30 s for
  each further consecutive failure. Any answer, a 404 included, ends the backoff. Cached entries are still served:
  `byUsername` throws `IdentityUnavailableException` ("Identity is unavailable (backing off)") for a name it must
  ask about, and `byIds` returns the cached subset.
- `oj.identity.client.skipped{call=by_username|by_ids}` counts the calls that were not sent;
  `oj.identity.client.failures{call}` counts the failed ones.

`PublicUser.avatar` is always null from identity SP14 on; the field stays for compatibility.

## 0.8.1: millisecond revocation cutoffs

Identity revokes every session of a user (role change, disable, delete) by writing two markers with the same TTL:

- `RedisKeys.tokenRevokedBefore(uid)` = `oj:token:revoked-before:<uid>`, the epoch second after the revocation
  (`RevocationCutoffs.secondsMarker`);
- `RedisKeys.tokenRevokedBeforeMillis(uid)` = `oj:token:revoked-before-ms:<uid>`, the epoch millisecond after it
  (`RevocationCutoffs.millisMarker`).

Access tokens carry `iat_ms` (`OjJwtDecoders.IAT_MS_CLAIM`), the same instant as `iat` in milliseconds.
`RevocationCutoffs.revoked(iat, iatMs, secondsCutoff, millisCutoff)` is the rule the gateway applies:

| Token | Markers present | Revoked when |
|---|---|---|
| has `iat_ms` | millisecond marker at least as new as the seconds marker (or no seconds marker) | `iat_ms < millisCutoff` |
| has `iat_ms` | millisecond marker older than the seconds marker (stale) | `iat < secondsCutoff` |
| has `iat_ms` | seconds marker only (an older identity) | `iat < secondsCutoff` |
| no `iat_ms` (minted before the upgrade) | seconds marker | `iat < secondsCutoff` |
| any | none | never |

So a login later in the same second as the revocation keeps working, and every mix of old and new identity and
gateway keeps at least today's whole-second rule. A millisecond marker counts only when
`(millisCutoff - 1) / 1000 >= secondsCutoff - 1`, which a pair written together always satisfies; an identity rolled
back alone, or a second write that failed, can leave a stale millisecond key that must not hide a newer seconds marker. A marker that is not a number throws `NumberFormatException`.
