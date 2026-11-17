package bioflix.user.repository;

import bioflix.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByAuth0Sub(String auth0Sub);

    List<User> findAllByDeletedAtIsNullOrderByCreatedAt();

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNullAndIdNot(String email, UUID id);
}
