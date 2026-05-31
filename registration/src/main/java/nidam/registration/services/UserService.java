package nidam.registration.services;

import nidam.registration.config.properties.AuthorizationProperties;
import nidam.registration.entities.sql.Authority;
import nidam.registration.entities.sql.User;
import nidam.registration.repositories.sql.AuthorityRepository;
import nidam.registration.repositories.sql.UserRepository;
import nidam.registration.services.dto.UserRegisteredDto;
import nidam.registration.services.dto.UserRegistrationCaptchaDto;
import nidam.registration.services.dto.UserRegistrationDto;
import nidam.registration.services.error.AlreadyExistException;
import nidam.registration.services.error.ReCaptchaException;
import nidam.registration.services.mapper.UserRegisteredSqlMapper;
import nidam.registration.services.mapper.UserRegistrationCaptchaMapper;
import nidam.registration.services.mapper.UserRegistrationSqlMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * SQL-based implementation of user registration logic.
 *
 * <p>Active when {@code nidam.persistence-mode=sql} (default).</p>
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validate CAPTCHA (if applicable)</li>
 *   <li>Check for duplicate email</li>
 *   <li>Map DTOs to entities</li>
 *   <li>Encode passwords</li>
 *   <li>Assign authorities</li>
 *   <li>Persist user</li>
 *   <li>Map entity to response DTO</li>
 * </ul>
 * </p>
 */
@Service
@ConditionalOnProperty(name = "nidam.persistence-mode", havingValue = "sql", matchIfMissing = true)
public class UserService extends UserServiceAbstract{

	private final Logger log = Logger.getLogger(UserService.class.getName());

	private final UserRepository userRepository;
	private final AuthorityRepository authorityRepository;

	private final UserRegistrationSqlMapper registrationMapper;
	private final UserRegisteredSqlMapper registeredMapper;
	private final UserRegistrationCaptchaMapper userRegistrationCaptchaMapper;
	private final RecaptchaService recaptchaService;
	private final AuthorizationProperties authorizationProperties;

	public UserService(UserRepository userRepository, AuthorityRepository authorityRepository,
	                   PasswordEncoder passwordEncoder, UserRegistrationSqlMapper registrationMapper, UserRegisteredSqlMapper registeredMapper,
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
	 * Registers a new user after validating the CAPTCHA.
	 *
	 * <p>Flow:
	 * <ol>
	 *   <li>Validate CAPTCHA using {@link RecaptchaService}</li>
	 *   <li>Map DTO to entity</li>
	 *   <li>Delegate to {@link #save(UserRegistrationDto)}</li>
	 * </ol>
	 * </p>
	 *
	 * @param userDto registration request containing CAPTCHA token
	 * @return the registered user DTO
	 * @throws ReCaptchaException if CAPTCHA validation fails
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
	 * Registers a new user.
	 *
	 * <p>Flow:
	 * <ol>
	 *   <li>Check if email is already used</li>
	 *   <li>Map DTO to {@link nidam.registration.entities.sql.User} entity</li>
	 *   <li>Validate and encode password</li>
	 *   <li>Assign all configured authorities</li>
	 *   <li>Enable the user</li>
	 *   <li>Persist the user</li>
	 *   <li>Map entity to {@link UserRegisteredDto}</li>
	 * </ol>
	 * </p>
	 *
	 * @param userDto registration request
	 * @return the registered user DTO
	 * @throws AlreadyExistException if email is already registered
	 */
	@Override
	public UserRegisteredDto save(UserRegistrationDto userDto){
		if(userRepository.findUserByEmail(userDto.getEmail()).isPresent()){
			throw new AlreadyExistException(format("Email %s already used!", userDto.getEmail()));
		}
		User user = registrationMapper.toEntity(userDto);

		setPassword(user);

		setAuthorities(user);

//		disabled until implemented
//		if( authorizationProperties.getAuthType() == 1){
//		} else if(authorizationProperties.getAuthType() == 2){
//			setRoles(user);
//		}

		user.setEnabled(true);

		user = userRepository.save(user);
//		log.info("entity: " + user);

		UserRegisteredDto dto = registeredMapper.toDto(user);
		log.info("return dto: " + dto);
		return dto;
	}

	/**
	 * Assigns all configured authorities to the given user.
	 *
	 * <p>Authorities are resolved from the database using names defined in
	 * {@link AuthorizationProperties}.</p>
	 *
	 * <p>Note: This method assigns <b>all</b> available authorities,
	 * typically intended for administrative users.</p>
	 *
	 * @param user the user entity to update
	 */
	private void setAuthorities(User user) {
		// Admin User should always have all the authorities
		List<Authority> allAuthorities = new ArrayList<>();

		for(String authority : authorizationProperties.getAuthorities()){
			allAuthorities.add(authorityRepository.findAuthorityByName(authority));
		}

		user.getAuthorities().addAll(allAuthorities);
	}

//	public User get(String email){
//		return userRepository.findUserByEmail(email).orElseThrow();
//	}
}
