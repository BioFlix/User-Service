package bioflix.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 200) String fullName,
        @Pattern(regexp = "^\\+?[0-9 ]{8,15}$", message = "must be a valid phone number") String phone,
        Boolean smsOptIn
) {
    public boolean wantsSms() {
        return Boolean.TRUE.equals(smsOptIn);
    }

    @AssertTrue(message = "a phone number is required to receive SMS")
    public boolean isPhoneGivenWhenSmsWanted() {
        return !wantsSms() || (phone != null && !phone.isBlank());
    }
}
