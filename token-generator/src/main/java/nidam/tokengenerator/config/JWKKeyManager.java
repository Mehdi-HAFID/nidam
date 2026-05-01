package nidam.tokengenerator.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.JSONObjectUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Utility class responsible for managing the application's RSA key material
 * using a JSON Web Key (JWK) file persisted on the filesystem.
 *
 * <p>This class follows a "load-or-create" strategy:
 * <ul>
 *     <li>If a JWK file already exists at {@code key/jwk.json}, it is loaded and reused.</li>
 *     <li>If no file exists, a new RSA key pair is generated, stored on disk,
 *     and then returned.</li>
 * </ul>
 *
 * <p>The generated key is stored in JWK (JSON Web Key) format using a {@link JWKSet}.
 * The persisted file includes both public and private key material, allowing
 * the application to maintain a stable signing key across restarts.</p>
 *
 * <p><strong>Security Note:</strong>
 * The file {@code key/jwk.json} contains private key material and must be treated
 * as sensitive. It should:
 * <ul>
 *     <li>Not be committed to version control</li>
 *     <li>Have restricted filesystem permissions</li>
 *     <li>Be protected appropriately in production environments</li>
 * </ul>
 *
 * <p>This implementation is designed to work seamlessly with Spring Authorization Server,
 * where the returned {@link RSAKey} is used to build a {@link JWKSet} and exposed via
 * the JWK Set endpoint for token verification.</p>
 *
 * <p><strong>Key characteristics:</strong>
 * <ul>
 *     <li>Uses RSA 2048-bit key pairs</li>
 *     <li>Automatically assigns a unique Key ID ({@code kid})</li>
 *     <li>Ensures key persistence across application restarts</li>
 *     <li>Avoids legacy JKS keystore complexity</li>
 * </ul>
 *
 * <p><strong>Typical usage:</strong>
 * <pre>{@code
 * RSAKey rsaKey = JWKKeyManager.loadOrCreate();
 * JWKSet jwkSet = new JWKSet(rsaKey);
 * }</pre>
 *
 * <p><strong>File location:</strong>
 * <pre>{@code
 * ./key/jwk.json
 * }</pre>
 *
 * <p>This class is thread-safe under typical application startup conditions,
 * where it is invoked once during bean initialization.</p>
 */
public class JWKKeyManager {

	private static final Logger log = Logger.getLogger(JWKKeyManager.class.getName());

	private static final Path PATH = Paths.get("key/jwk.json");

	public static RSAKey loadOrCreate() {

		try {
			Files.createDirectories(PATH.getParent());
			// 1. load if exists
			if (Files.exists(PATH)) {
				log.info("Loading existing JWK file.");
				return loadFromFile();
			}

			// 2. create new
			log.info("Creating JWK file.");
			RSAKey rsaKey = generateRSAKey();
			saveToFile(rsaKey);

			return rsaKey;

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static RSAKey generateRSAKey() throws NoSuchAlgorithmException {

		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);

		KeyPair keyPair = generator.generateKeyPair();

		RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
		RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
		RSAKey rsaKey = new RSAKey.Builder(publicKey)
				.privateKey(privateKey)
				.keyID(UUID.randomUUID().toString())
				.build();

		return rsaKey;
	}

	private static void saveToFile(RSAKey rsaKey) throws IOException {
		JWKSet jwkSet = new JWKSet(rsaKey);

		String json = JSONObjectUtils.toJSONString(jwkSet.toJSONObject(false));

		Files.writeString(PATH, json);
	}

	private static RSAKey loadFromFile() throws IOException, ParseException {
		String content = Files.readString(PATH);
		JWKSet jwkSet = JWKSet.parse(content);

		return (RSAKey) jwkSet.getKeys().getFirst();
	}


}
