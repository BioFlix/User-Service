package bioflix.user.controller;

import bioflix.user.dto.TokenValidationResponse;
import bioflix.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
