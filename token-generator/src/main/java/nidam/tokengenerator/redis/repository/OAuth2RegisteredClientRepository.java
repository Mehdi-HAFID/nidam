package nidam.tokengenerator.redis.repository;

import nidam.tokengenerator.redis.entity.OAuth2RegisteredClient;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OAuth2RegisteredClientRepository extends CrudRepository<OAuth2RegisteredClient, String> {

	OAuth2RegisteredClient findByClientId(String clientId);

}
