package vn.thanhtuanle.oj.common.identity;

import java.time.LocalDateTime;
import java.util.UUID;

/** Identity's public profile of a user: the JSON of {@code /api/v1/users/public}. */
public record PublicUser(UUID id, String username, String name, String avatar, LocalDateTime createdAt) {
}
