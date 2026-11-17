package bioflix.user.service;

import bioflix.user.dto.TokenValidationResponse;
import bioflix.user.dto.UpdateUserRequest;
import bioflix.user.dto.UserResponse;
import bioflix.user.entity.Role;
import bioflix.user.entity.User;
import bioflix.user.exception.ConflictException;
import bioflix.user.exception.ForbiddenException;
import bioflix.user.exception.InvalidTokenException;
import bioflix.user.exception.NotFoundException;
import bioflix.user.outbox.UserEvents;
import bioflix.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserEvents userEvents;
    private final TransactionTemplate newTransaction;
    private final String emailClaim;
    private final String nameClaim;
    private final Set<String> adminEmails;

    public UserService(UserRepository userRepository,
                       UserEvents userEvents,
                       PlatformTransactionManager transactionManager,
                       @Value("${app.auth0.email-claim}") String emailClaim,
                       @Value("${app.auth0.name-claim}") String nameClaim,
                       @Value("${app.admin-emails:}") String adminEmails) {
        this.userRepository = userRepository;
        this.userEvents = userEvents;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.emailClaim = emailClaim;
        this.nameClaim = nameClaim;
        this.adminEmails = Arrays.stream(adminEmails.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    public User getOrCreate(Jwt jwt) {
        User user = userRepository.findByAuth0Sub(jwt.getSubject())
                .orElseGet(() -> {
                    try {
                        return newTransaction.execute(status -> register(jwt));
                    } catch (DataIntegrityViolationException ex) {
                        // Parallel first login: another request created the user, or the e-mail is taken
                        return userRepository.findByAuth0Sub(jwt.getSubject())
                                .orElseThrow(() -> new ConflictException("Could not create user – e-mail already in use?"));
                    }
                });
        if (user.isDeleted()) {
            throw new ForbiddenException("This account has been deleted");
        }
        return user;
    }

    private User register(Jwt jwt) {
        String email = jwt.getClaimAsString(emailClaim);
        if (email == null || email.isBlank()) {
            throw new InvalidTokenException("The token has no e-mail claim (" + emailClaim + ") – see the Auth0 Action in the README");
        }
        String name = jwt.getClaimAsString(nameClaim);
        User user = new User(jwt.getSubject(), email, name == null || name.isBlank() ? email : name);
        if (adminEmails.contains(email.toLowerCase(Locale.ROOT))) {
            user.setRole(Role.ADMIN);
        }
        User saved = userRepository.saveAndFlush(user);
        userEvents.registered(saved);
        return saved;
    }

    public TokenValidationResponse validate(Jwt jwt) {
        User user = getOrCreate(jwt);
        return new TokenValidationResponse(user.getId(), user.getAuth0Sub(), user.getEmail(), user.getRole());
    }

    public void requireAdmin(User user) {
        if (user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only ADMIN may do this");
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return UserResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAll() {
        return userRepository.findAllByDeletedAtIsNullOrderByCreatedAt().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = findOrThrow(id);

        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNullAndIdNot(request.email(), id)) {
            throw new ConflictException("Email " + request.email() + " is already in use");
        }

        String phone = request.phone() == null || request.phone().isBlank() ? null : request.phone();
        boolean changed = !Objects.equals(user.getEmail(), request.email())
                || !Objects.equals(user.getFullName(), request.fullName())
                || !Objects.equals(user.getPhone(), phone)
                || user.isSmsOptIn() != request.wantsSms();
        if (!changed) {
            return UserResponse.from(user);
        }

        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setPhone(phone);
        user.setSmsOptIn(request.wantsSms());
        userRepository.flush();
        userEvents.updated(user);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse changeRole(UUID id, Role role) {
        User user = findOrThrow(id);
        user.setRole(role);
        return UserResponse.from(user);
    }

    @Transactional
    public void delete(UUID id) {
        User user = findOrThrow(id);
        user.markDeleted();
        userEvents.deleted(id);
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }
}
