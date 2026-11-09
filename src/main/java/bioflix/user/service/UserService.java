package bioflix.user.service;

import bioflix.user.dto.TokenValidationResponse;
import bioflix.user.dto.UpdateUserRequest;
import bioflix.user.dto.UserResponse;
import bioflix.user.entity.User;
import bioflix.user.exception.ConflictException;
import bioflix.user.exception.NotFoundException;
import bioflix.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final String emailClaim;
    private final String nameClaim;

    public UserService(UserRepository userRepository,
                       @Value("${app.auth0.email-claim}") String emailClaim,
                       @Value("${app.auth0.name-claim}") String nameClaim) {
        this.userRepository = userRepository;
        this.emailClaim = emailClaim;
        this.nameClaim = nameClaim;
    }

    /** Finds the local user for an Auth0 token – or creates it on first login. */
    @Transactional
    public User getOrCreate(Jwt jwt) {
        return userRepository.findByAuth0Id(jwt.getSubject())
                .orElseGet(() -> register(jwt));
    }

    private User register(Jwt jwt) {
        User user = new User(jwt.getSubject(), jwt.getClaimAsString(emailClaim), jwt.getClaimAsString(nameClaim));
        User saved = userRepository.save(user);
        // TODO (RabbitMQ): publish user.registered
        return saved;
    }

    @Transactional
    public TokenValidationResponse validate(Jwt jwt) {
        User user = getOrCreate(jwt);
        return new TokenValidationResponse(user.getId(), user.getAuth0Id(), user.getEmail(), user.getRole());
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return UserResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAll() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = findOrThrow(id);

        if (userRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new ConflictException("Email " + request.email() + " is already in use");
        }

        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setPhone(request.phone());

        // TODO (RabbitMQ): publish user.updated
        return UserResponse.from(user);
    }

    @Transactional
    public void delete(Long id) {
        User user = findOrThrow(id);
        userRepository.delete(user);
        // TODO (RabbitMQ): publish user.deleted
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }
}
