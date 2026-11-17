package bioflix.user.dto;

import bioflix.user.entity.Role;
import bioflix.user.entity.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        boolean smsOptIn,
        Role role,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.isSmsOptIn(),
                user.getRole(),
                user.getCreatedAt());
    }
}
