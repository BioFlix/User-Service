package bioflix.user.dto;

import bioflix.user.entity.Role;

import java.util.UUID;

public record TokenValidationResponse(
        UUID userId,
        String auth0Sub,
        String email,
        Role role
) {
}
