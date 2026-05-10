package nidam.registration.repositories.mongo;

import nidam.registration.entities.mongo.Authority;
import org.bson.types.ObjectId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MongoDB repository for {@code Authority} entities.
 *
 * <p>This repository is conditionally loaded when:
 * <pre>
 * nidam.persistence-mode=mongo
 * </pre>
 *
 * <p>Uses MongoDB-specific identifier type {@code ObjectId}.
 *
 * <p>Provides query methods for:
 * <ul>
 *   <li>Finding an authority by its name</li>
 *   <li>Fetching multiple authorities by their ObjectId list</li>
 * </ul>
 *
 * <p>This repository is part of the MongoDB persistence implementation and is
 * mutually exclusive with the SQL-based {@code AuthorityRepository}.
 */
@Repository
@ConditionalOnProperty(name = "nidam.persistence-mode", havingValue = "mongo")
public interface AuthorityRepository extends MongoRepository<Authority, ObjectId> {

	Authority findAuthorityByName(String name);

	List<Authority> findByIdIn(List<ObjectId> ids);


}
