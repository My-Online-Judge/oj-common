package vn.thanhtuanle.oj.common.identity;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Identity's public profile of a user: the JSON of {@code /api/v1/users/public}.
 *
 * <p>{@code avatar} is always null from identity SP14 on (public reads carry no third-party URL); kept for
 * compatibility.
 */
public record PublicUser(UUID id, String username, String name, String avatar, LocalDateTime createdAt) {
}
