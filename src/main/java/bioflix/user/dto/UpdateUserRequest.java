package bioflix.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(max = 100) String fullName,
        @Pattern(regexp = "^\\+?[0-9 ]{8,15}$", message = "must be a valid phone number") String phone
) {
}
