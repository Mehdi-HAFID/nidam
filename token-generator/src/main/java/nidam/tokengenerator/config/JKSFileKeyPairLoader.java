package nidam.tokengenerator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;

import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.logging.Logger;

public class JKSFileKeyPairLoader {

	private static Logger log = Logger.getLogger(JKSFileKeyPairLoader.class.getName());

	@Value("${password}")
	private static String password;

	@Value("${privateKey}")
	private static String privateKey;

	@Value("${alias}")
	private static String alias;

	public static KeyPair loadKeyStore() throws Exception {
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
