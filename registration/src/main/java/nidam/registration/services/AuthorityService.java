package nidam.registration.services;

import nidam.registration.config.properties.AuthorizationProperties;
import nidam.registration.entities.sql.Authority;
import nidam.registration.repositories.sql.AuthorityRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * SQL-based implementation of {@link AuthorityServiceSpec}.
 *
 * <p>Active when {@code nidam.persistence-mode=sql} (default).</p>
 *
 * <p>This service synchronizes authorities defined in
 * {@link AuthorizationProperties} with the relational database.</p>
 *
 * <p>Behavior:
 * <ul>
 *   <li>Fetch all existing authorities</li>
 *   <li>Insert missing ones</li>
 *   <li>Skip already persisted authorities</li>
 * </ul>
 * </p>
 */
@Service
@ConditionalOnProperty(name = "nidam.persistence-mode", havingValue = "sql", matchIfMissing = true)
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
		// 1. get all, 2 loop nidamProperties.authorities, if exists skip, if not create.
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
