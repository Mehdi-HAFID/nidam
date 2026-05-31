package nidam.registration.services;

import nidam.registration.services.dto.UserRegisteredDto;
import nidam.registration.services.dto.UserRegistrationCaptchaDto;
import nidam.registration.services.dto.UserRegistrationDto;

/**
 * Contract for user registration operations.
 *
 * <p>Defines methods for registering users with or without CAPTCHA validation,
 * independent of the persistence layer.</p>
 */
public interface UserServiceSpec {

	/**
	 * Registers a user with CAPTCHA validation.
	 *
	 * @param userDto the registration request including CAPTCHA
	 * @return the registered user DTO
	 */
	UserRegisteredDto save(UserRegistrationCaptchaDto userDto);

	/**
	 * Registers a user without CAPTCHA validation.
	 *
	 * @param userDto the registration request
	 * @return the registered user DTO
	 */
	UserRegisteredDto save(UserRegistrationDto userDto);
}
