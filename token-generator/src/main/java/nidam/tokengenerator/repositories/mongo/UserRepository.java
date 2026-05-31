package nidam.tokengenerator.repositories.mongo;

import nidam.tokengenerator.entities.mongo.User;
import org.bson.types.ObjectId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "nidam.persistence-mode", havingValue = "mongo")
public interface UserRepository extends MongoRepository<User, ObjectId> {

	Optional<User> findByEmail(String email);

}
