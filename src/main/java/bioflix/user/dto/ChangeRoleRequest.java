package bioflix.user.dto;

import bioflix.user.entity.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull Role role) {
}
