package nidam.registration.services;

import nidam.registration.config.properties.AuthorizationProperties;
import nidam.registration.entities.Authority;
import nidam.registration.repositories.AuthorityRepository;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class AuthorityService {

	private final Logger log = Logger.getLogger(AuthorityService.class.getName());

	private final AuthorityRepository authorityRepo;
	private final AuthorizationProperties authorizationProperties;

	public AuthorityService(AuthorityRepository authorityRepository, AuthorizationProperties authorizationProperties) {
		this.authorityRepo = authorityRepository;
		this.authorizationProperties = authorizationProperties;
	}

	public void addToDatabase(){
		// 1. get all, 2 loop nidamProperties.authorities, if exists skip, if not create.
		Set<String> existing = authorityRepo.findAll().stream().map(Authority::getName).collect(Collectors.toSet());
		for(String authority : authorizationProperties.getAuthorities()){
//			for(String authority : persistedAuthorities){
				if (existing.contains(authority)){
					log.info(authority + " is already persisted");
				} else {
					log.info("persisting authority " + authority + " to Database");
					authorityRepo.save(new Authority(authority));
				}
//			}
		}

	}
}
