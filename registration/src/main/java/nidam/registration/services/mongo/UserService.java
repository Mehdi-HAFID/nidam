package nidam.registration.services.mongo;

import nidam.registration.config.properties.AuthorizationProperties;
import nidam.registration.entities.mongo.Authority;
import nidam.registration.entities.mongo.User;
import nidam.registration.repositories.mongo.AuthorityRepository;
import nidam.registration.repositories.mongo.UserRepository;
import nidam.registration.services.RecaptchaService;
import nidam.registration.services.UserServiceAbstract;
import nidam.registration.services.dto.UserRegisteredDto;
import nidam.registration.services.dto.UserRegistrationCaptchaDto;
import nidam.registration.services.dto.UserRegistrationDto;
import nidam.registration.services.error.AlreadyExistException;
import nidam.registration.services.error.ReCaptchaException;
import nidam.registration.services.mapper.UserRegisteredMongoMapper;
import nidam.registration.services.mapper.UserRegistrationCaptchaMapper;
import nidam.registration.services.mapper.UserRegistrationMongoMapper;
import org.bson.types.ObjectId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * MongoDB-based implementation of user registration logic.
 *
 * <p>Active when {@code nidam.persistence-mode=mongo}.</p>
 *
 * <p>Follows the same workflow as the SQL implementation, with differences:
 * <ul>
 *   <li>Uses ObjectId references for authorities</li>
 *   <li>Resolves authority names separately for DTO mapping</li>
 * </ul>
 * </p>
 */
@Service
@ConditionalOnProperty(name = "nidam.persistence-mode", havingValue = "mongo")
public class UserService extends UserServiceAbstract {

	private final Logger log = Logger.getLogger(UserService.class.getName());

	private final UserRepository userRepository;
	private final AuthorityRepository authorityRepository;
	private final UserRegistrationMongoMapper registrationMapper;
	private final UserRegisteredMongoMapper registeredMapper;

	private final UserRegistrationCaptchaMapper userRegistrationCaptchaMapper;
	private final RecaptchaService recaptchaService;
	private final AuthorizationProperties authorizationProperties;

	public UserService(UserRepository userRepository, AuthorityRepository authorityRepository, PasswordEncoder passwordEncoder,
	                   UserRegistrationMongoMapper registrationMapper, UserRegisteredMongoMapper registeredMapper,
	                   UserRegistrationCaptchaMapper userRegistrationCaptchaMapper, RecaptchaService recaptchaService,
	                   AuthorizationProperties authorizationProperties) {
		super(passwordEncoder);

		this.userRepository = userRepository;
		this.authorityRepository = authorityRepository;
		this.registrationMapper = registrationMapper;
		this.registeredMapper = registeredMapper;
		this.userRegistrationCaptchaMapper = userRegistrationCaptchaMapper;
		this.recaptchaService = recaptchaService;
		this.authorizationProperties = authorizationProperties;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public UserRegisteredDto save(UserRegistrationCaptchaDto userDto){
		boolean result = recaptchaService.validateCaptcha(userDto.getRecaptchaKey());
//		log.info("result: " + result);
		if(!result){
			throw new ReCaptchaException("Captcha Error");
		}
		return save(userRegistrationCaptchaMapper.toEntity(userDto));
	}

	/**
	 * Registers a new user using MongoDB persistence.
	 *
	 * <p>Flow is similar to the SQL implementation with key differences:
	 * <ul>
	 *   <li>Authorities are stored as {@code ObjectId} references</li>
	 *   <li>Authority names are resolved separately for DTO mapping</li>
	 * </ul>
	 * </p>
	 *
	 * @param userDto registration request
	 * @return the registered user DTO
	 * @throws AlreadyExistException if email is already registered
	 */
	@Override
	public UserRegisteredDto save(UserRegistrationDto userDto){
		// Dependent on userRepo
		if(userRepository.findByEmail(userDto.getEmail()).isPresent()){
			throw new AlreadyExistException(format("Email %s already used!", userDto.getEmail()));
		}
		// Dependent on registrationMapper
		User user = registrationMapper.toEntity(userDto);

		// Common because method can be overloaded in superclass
		setPassword(user);

		setAuthorities(user);

		user.setEnabled(true);

		user = userRepository.save(user);

//		log.info("mongo user: " + user);

		List<String> authorityNames = authorityRepository.findByIdIn(user.getAuthoritiesIds())
				.stream().map(Authority::getName).toList();;
//		log.info("authorityNames: " + authorityNames);

		UserRegisteredDto dto = registeredMapper.toDto(user, authorityNames);
		log.info("return dto: " + dto);
		return dto;
	}

	/**
	 * Assigns all configured authorities to the given user.
	 *
	 * <p>Instead of embedding full authority objects, this method stores
	 * their {@code ObjectId} references.</p>
	 *
	 * @param user the Mongo user entity to update
	 */
	private void setAuthorities(User user) {
		List<ObjectId> allAuthoritiesIds = new ArrayList<>();

		for(String authority : authorizationProperties.getAuthorities()){
			allAuthoritiesIds.add(authorityRepository.findAuthorityByName(authority).getId());
		}

		user.getAuthoritiesIds().addAll(allAuthoritiesIds);
	}

}
