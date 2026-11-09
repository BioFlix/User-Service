package bioflix.user.dto;

import bioflix.user.entity.Role;

/**
 * Returned to the API gateway. The gateway forwards userId as X-User-Id
 * (and role as X-User-Role) to the downstream services.
 */
public record TokenValidationResponse(
        Long userId,
        String auth0Id,
        String email,
        Role role
) {
}
