package bioflix.user.controller;

import bioflix.user.dto.TokenValidationResponse;
import bioflix.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by the API gateway for every incoming request:
 *   GET /api/auth/validate  with header  Authorization: Bearer <Auth0 token>
 *
 * 200 -> token is valid, body contains the internal user id
 * 401 -> token invalid/expired (handled by Spring Security before we get here)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/validate")
    public TokenValidationResponse validate(@AuthenticationPrincipal Jwt jwt) {
        return userService.validate(jwt);
    }
}
