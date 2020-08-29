package moe.yushi.yggdrasil_mock;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

public final class KeyUtils {
	private KeyUtils() {}

	public static KeyPair generateKey() {
		try {
			var gen = KeyPairGenerator.getInstance("RSA");
			gen.initialize(4096, new SecureRandom());
			return gen.genKeyPair();
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public static String toPEMPublicKey(PublicKey key) {
		var encoded = ((RSAPublicKey) key).getEncoded();
		return "-----BEGIN PUBLIC KEY-----\n" +
				Base64.getMimeEncoder(76, new byte[] { '\n' }).encodeToString(encoded) +
				"\n-----END PUBLIC KEY-----\n";
	}
}
