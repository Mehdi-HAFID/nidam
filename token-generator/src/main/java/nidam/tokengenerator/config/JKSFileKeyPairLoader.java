package nidam.tokengenerator.config;

import org.springframework.core.io.ClassPathResource;

import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.logging.Logger;

/**
 * Utility class for loading a {@link KeyPair} from a Java KeyStore (JKS) file.
 */
public class JKSFileKeyPairLoader {

	private static Logger log = Logger.getLogger(JKSFileKeyPairLoader.class.getName());

	/**
	 * Loads a {@link KeyPair} (RSA) from the specified JKS file.
	 *
	 * @param privateKey the name of the keystore file in the classpath
	 * @param password   the password used to access the keystore and the key
	 * @param alias      the alias for the key within the keystore
	 * @return the loaded key pair
	 * @throws Exception if the keystore or keys cannot be loaded
	 */
	public static KeyPair loadKeyStore(String privateKey, String password, String alias) throws Exception {
		final KeyStore keystore = KeyStore.getInstance("JKS");

		keystore.load(new ClassPathResource(privateKey).getInputStream(), password.toCharArray());

		final PrivateKey key = (PrivateKey) keystore.getKey(alias, password.toCharArray());
		log.info("PrivateKey key: " + key);

		final Certificate cert = keystore.getCertificate(alias);
		final PublicKey publicKey = cert.getPublicKey();
		log.info("PublicKey publicKey: " + publicKey);
		return new KeyPair(publicKey, key);

	}
}
