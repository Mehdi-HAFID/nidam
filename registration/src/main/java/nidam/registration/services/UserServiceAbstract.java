package nidam.registration.services;

import nidam.registration.entities.sql.User;
import nidam.registration.services.error.PasswordInvalidException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Base implementation for {@link UserServiceSpec} providing shared logic.
 *
 * <p>Contains common functionality across SQL and Mongo implementations,
 * such as password validation and encoding.</p>
 *
 * <p>Uses method overloading to support different entity types
 * without introducing generics complexity.</p>
 */
public abstract class UserServiceAbstract implements UserServiceSpec{

	private final PasswordEncoder passwordEncoder;

	public UserServiceAbstract(PasswordEncoder passwordEncoder) {
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * Encodes and sets the password for SQL user entities.
	 *
	 * @param user the SQL user entity
	 * @throws PasswordInvalidException if password constraints are violated
	 */
	protected void setPassword(User user) {
		if(!validatePassword(user.getPassword())){
			throw new PasswordInvalidException("Password violate constraints");
		}
		String encoded = passwordEncoder.encode(user.getPassword());
		user.setPassword(encoded);
	}

	/**
	 * Encodes and sets the password for Mongo user entities.
	 *
	 * @param user the Mongo user entity
	 * @throws PasswordInvalidException if password constraints are violated
	 */
	protected void setPassword(nidam.registration.entities.mongo.User user) {
		if(!validatePassword(user.getPassword())){
			throw new PasswordInvalidException("Password violate constraints");
		}
		String encoded = passwordEncoder.encode(user.getPassword());
		user.setPassword(encoded);
	}

	/**
	 * Validates password constraints.
	 *
	 * @param password raw password
	 * @return true if valid, false otherwise
	 */
	protected boolean validatePassword(String password){
		if(password.length() < 2){
			return false;
		}
		return true;
	}
}
