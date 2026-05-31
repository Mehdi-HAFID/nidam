package nidam.registration.repositories.mongo;

import nidam.registration.entities.mongo.User;
import org.bson.types.ObjectId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * MongoDB repository for {@code User} entities.
 *
 * <p>This repository is conditionally loaded when:
 * <pre>
 * nidam.persistence-mode=mongo
 * </pre>
 *
 * <p>Uses {@code ObjectId} as the primary identifier type.
 *
 * <p>Provides query methods for:
 * <ul>
 *   <li>Retrieving a user by email address</li>
 * </ul>
 *
 * <p>This repository is part of the MongoDB persistence implementation and replaces
 * the SQL-based {@code UserRepository} when Mongo mode is active.
 */
@Repository
@ConditionalOnProperty(name = "nidam.persistence-mode", havingValue = "mongo")
public interface UserRepository extends MongoRepository<User, ObjectId> {

	Optional<User> findByEmail(String email);
}
