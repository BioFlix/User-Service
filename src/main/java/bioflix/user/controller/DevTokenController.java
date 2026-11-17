package bioflix.user.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@Profile("dev")
@RequestMapping("/api/dev")
public class DevTokenController {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String emailClaim;
    private final String nameClaim;

    public DevTokenController(JwtEncoder jwtEncoder,
                              @Value("${app.dev.jwt-issuer}") String issuer,
                              @Value("${app.auth0.email-claim}") String emailClaim,
                              @Value("${app.auth0.name-claim}") String nameClaim) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.emailClaim = emailClaim;
        this.nameClaim = nameClaim;
    }

    @PostMapping("/token")
    public Map<String, Object> token(@Valid @RequestBody DevTokenRequest request) {
        Instant now = Instant.now();
        Duration validFor = Duration.ofHours(8);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(request.sub())
                .issuedAt(now)
                .expiresAt(now.plus(validFor));
        if (request.email() != null) {
            claims.claim(emailClaim, request.email());
        }
        if (request.name() != null) {
            claims.claim(nameClaim, request.name());
        }
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
        return Map.of("access_token", token, "token_type", "Bearer", "expires_in", validFor.toSeconds());
    }

    public record DevTokenRequest(@NotBlank String sub, String email, String name) {
    }
}
