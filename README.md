# oj-common

Code shared by the My Online Judge services.

- `event` — records published on `oj.submission.events`.
- `grpc` — the internal problem-service API (`src/main/proto`, stubs generated at build time) and the
  service-token interceptors every internal call goes through.
- `security`, `client`, `redis` — JWT verification through JWKS, `CurrentUser`, client fingerprints,
  shared Redis keys.

Build with `./mvnw` (Maven 3.9.9): the protobuf plugin needs Maven ≥ 3.9.6 and runs glibc binaries
(protoc, protoc-gen-grpc-java), so Docker builds use `maven:3.9-eclipse-temurin-17`, not the Alpine image.
