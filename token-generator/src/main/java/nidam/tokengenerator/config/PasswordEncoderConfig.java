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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Builds the {@link PasswordEncoder} bean from the {@code password-encoders} /
 * {@code password-idless} properties instead of hardcoding an algorithm, so the
 * active hashing scheme (and which legacy schemes remain verifiable) is a config
 * change, not a redeploy.
 */
@Configuration
public class PasswordEncoderConfig {

	private Logger log = Logger.getLogger(PasswordEncoderConfig.class.getName());

	/**
	 * Assembles a {@link DelegatingPasswordEncoder} from {@code passwordProperties}.
	 * <p>
	 * bcrypt is force-included even if omitted from {@code password-encoders}: OAuth2
	 * client-credentials secret verification requires it, and Spring Authorization
	 * Server only wires up a single {@code PasswordEncoder} bean, so bcrypt support
	 * has to live inside this delegating encoder rather than a second bean.
	 * <p>
	 * The first entry in {@code password-encoders} is the encoder used for new hashes
	 * (via {@link DelegatingPasswordEncoder}'s {@code idForEncode}); the rest are kept
	 * only so existing hashes under those ids can still be matched.
	 * <p>
	 * {@code password-idless} sets the fallback encoder for hashes with no {@code {id}}
	 * prefix, via {@link DelegatingPasswordEncoder#setDefaultPasswordEncoderForMatches},
	 * to support verifying pre-migration hashes that predate the {id} format.
	 *
	 * @throws IllegalArgumentException if {@code password-encoders} is empty, contains
	 *                                  an unsupported algorithm name, or if
	 *                                  {@code password-idless} is missing/blank or
	 *                                  names an unsupported algorithm
	 */
	@Bean
	public PasswordEncoder passwordEncoder(PasswordProperties passwordProperties) {
//		List<String> encoders = passwordProperties.getEncoders();
		List<String> encoders = new ArrayList<>(passwordProperties.getEncoders());
		log.info("encoders: " + encoders);

		Map<String, Supplier<PasswordEncoder>> encoderSuppliers = Map.of(
				"bcrypt", () -> new BCryptPasswordEncoder(),
				"argon2", () -> Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
				"pbkdf2", () -> Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
				"scrypt", () -> SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8()
		);

		if (encoders.isEmpty()) {
			throw new IllegalArgumentException("password-encoders must contain at least one password encoder");
		}

		List<String> unsupportedEncoders = encoders.stream().filter(encoder -> !encoderSuppliers.containsKey(encoder)).toList();

		if (!unsupportedEncoders.isEmpty()) {
			throw new IllegalArgumentException("Unsupported password encoder(s): " + unsupportedEncoders);
		}

		// OAuth Client Credentials Support requires bcrypt, this is Nidam internal. cannot use a 2nd PE Bean because SAS does not support > 1
		if(!encoders.contains("bcrypt")){
			encoders.add("bcrypt");
		}

		Map<String, PasswordEncoder> encodersMapping = encoders.stream()
				.filter(key1 -> encoderSuppliers.containsKey(key1))
				.collect(Collectors.toMap(Function.identity(), key -> encoderSuppliers.get(key).get()));


		// first in list used to encode
		DelegatingPasswordEncoder passwordEncoder = new DelegatingPasswordEncoder(encoders.getFirst(), encodersMapping);

		// use this encoder if {id} does not exist
		String idlessEncoder = passwordProperties.getIdless();
		if (idlessEncoder == null || idlessEncoder.trim().isEmpty()) {
			throw new IllegalArgumentException("password-idless must specify a password encoder");
		}

		Supplier<PasswordEncoder> idlessEncoderSupplier = encoderSuppliers.get(idlessEncoder);
		if (idlessEncoderSupplier == null) {
			throw new IllegalArgumentException("Unsupported password-idless encoder: " + idlessEncoder);
		}

		passwordEncoder.setDefaultPasswordEncoderForMatches(idlessEncoderSupplier.get());
		return passwordEncoder;
	}
}
