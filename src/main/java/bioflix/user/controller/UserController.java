package bioflix.user.controller;

import bioflix.user.dto.ChangeRoleRequest;
import bioflix.user.dto.UpdateUserRequest;
import bioflix.user.dto.UserResponse;
import bioflix.user.entity.Role;
import bioflix.user.entity.User;
import bioflix.user.exception.ForbiddenException;
import bioflix.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(userService.getOrCreate(jwt));
    }

    @PutMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateUserRequest request) {
        User user = userService.getOrCreate(jwt);
        return userService.update(user.getId(), request);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal Jwt jwt) {
        User user = userService.getOrCreate(jwt);
        userService.delete(user.getId());
        return ResponseEntity.noContent().build();
    }

    // ---- ADMIN  ----

    @GetMapping
    public List<UserResponse> getAll(@AuthenticationPrincipal Jwt jwt) {
        userService.requireAdmin(userService.getOrCreate(jwt));
        return userService.getAll();
    }

    @GetMapping("/{id}")
    public UserResponse getById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        User caller = userService.getOrCreate(jwt);
        if (!caller.getId().equals(id)) {
            userService.requireAdmin(caller);
        }
        return userService.getById(id);
    }

    @PutMapping("/{id}/role")
    public UserResponse changeRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                   @Valid @RequestBody ChangeRoleRequest request) {
        User caller = userService.getOrCreate(jwt);
        userService.requireAdmin(caller);
        if (caller.getId().equals(id) && request.role() != Role.ADMIN) {
            throw new ForbiddenException("An admin cannot remove their own ADMIN role");
        }
        return userService.changeRole(id, request.role());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        userService.requireAdmin(userService.getOrCreate(jwt));
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
