package nidam.tokengenerator.repositories.mongo;

import nidam.tokengenerator.entities.mongo.Authority;
import org.bson.types.ObjectId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@ConditionalOnProperty(name = "nidam.persistence-mode", havingValue = "mongo")
public interface AuthorityRepository extends MongoRepository<Authority, ObjectId> {
	List<Authority> findByIdIn(List<ObjectId> ids);
}
