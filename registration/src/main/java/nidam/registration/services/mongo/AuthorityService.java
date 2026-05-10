package nidam.registration.services.mongo;

import nidam.registration.config.properties.AuthorizationProperties;
import nidam.registration.entities.mongo.Authority;
import nidam.registration.repositories.mongo.AuthorityRepository;
import nidam.registration.services.AuthorityServiceSpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * MongoDB-based implementation of {@link AuthorityServiceSpec}.
 *
 * <p>Active when {@code nidam.persistence-mode=mongo}.</p>
 *
 * <p>Performs the same synchronization logic as the SQL implementation,
 * adapted to MongoDB repositories.</p>
 */
@Service
@ConditionalOnProperty(name = "nidam.persistence-mode", havingValue = "mongo")
public class AuthorityService implements AuthorityServiceSpec {
	private final Logger log = Logger.getLogger(AuthorityService.class.getName());

	private final AuthorityRepository authorityRepo;
	private final AuthorizationProperties authorizationProperties;

	public AuthorityService(AuthorityRepository authorityRepository, AuthorizationProperties authorizationProperties) {
		this.authorityRepo = authorityRepository;
		this.authorizationProperties = authorizationProperties;
	}

	@Override
	public void addToDatabase(){
		Set<String> existing = authorityRepo.findAll().stream().map(Authority::getName).collect(Collectors.toSet());
		for(String authority : authorizationProperties.getAuthorities()){
			if (existing.contains(authority)){
				log.info(authority + " is already persisted");
			} else {
				log.info("persisting authority " + authority + " to Database");
				authorityRepo.save(new Authority(authority));
			}
		}
	}


}
