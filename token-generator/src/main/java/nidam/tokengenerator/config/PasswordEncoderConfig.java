package nidam.tokengenerator.config;

import nidam.tokengenerator.config.properties.PasswordProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Configuration
public class PasswordEncoderConfig {

	private Logger log = Logger.getLogger(PasswordEncoderConfig.class.getName());

	@Bean
	public PasswordEncoder passwordEncoder(PasswordProperties passwordProperties) {
		log.info("encoders: " + passwordProperties.getEncoders());

		Map<String, Supplier<PasswordEncoder>> encoderSuppliers = Map.of(
				"bcrypt", () -> new BCryptPasswordEncoder(),
				"argon2", () -> Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
				"pbkdf2", () -> Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
				"scrypt", () -> SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8()
		);

		Map<String, PasswordEncoder> encodersMapping = passwordProperties.getEncoders().stream()
				.filter(key1 -> encoderSuppliers.containsKey(key1))
				.collect(Collectors.toMap(Function.identity(), key -> encoderSuppliers.get(key).get()));


		// first in list used to encode
		DelegatingPasswordEncoder passwordEncoder = new DelegatingPasswordEncoder(passwordProperties.getEncoders().getFirst(), encodersMapping);
		// use this encoder if {id} does not exist
		passwordEncoder.setDefaultPasswordEncoderForMatches(
				encoderSuppliers.getOrDefault(passwordProperties.getIdless(), () -> SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8()).get()
		);
		return passwordEncoder;
	}
}
