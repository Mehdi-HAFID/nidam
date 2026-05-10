package nidam.registration.repositories.sql;

import nidam.registration.entities.sql.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA repository for {@code User} entities.
 *
 * <p>This repository is conditionally loaded when:
 * <pre>
 * nidam.persistence-mode=sql
 * </pre>
 * or when the property is not explicitly set (defaults to SQL mode).
 *
 * <p>Uses {@code Long} as the primary key type, consistent with relational databases.
 *
 * <p>Provides query methods for:
 * <ul>
 *   <li>Retrieving a user by email address</li>
 * </ul>
 *
 * <p>This repository is part of the SQL persistence implementation and replaces
 * the MongoDB-based repository when SQL mode is active.
 */
@Repository
@ConditionalOnProperty(name = "nidam.persistence-mode", havingValue = "sql", matchIfMissing = true)
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findUserByEmail(String email);
}