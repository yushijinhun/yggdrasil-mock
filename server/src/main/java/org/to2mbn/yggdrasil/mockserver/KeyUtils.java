package org.to2mbn.yggdrasil.mockserver;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

public final class KeyUtils {

	public static KeyPair generateKey() {
		try {
			KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
			gen.initialize(4096, new SecureRandom());
			return gen.genKeyPair();
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public static String toPEMPublicKey(PublicKey key) {
		byte[] encoded = ((RSAPublicKey) key).getEncoded();
		return "-----BEGIN PUBLIC KEY-----\n" +
				Base64.getMimeEncoder(76, new byte[] { '\n' }).encodeToString(encoded) +
				"\n-----END PUBLIC KEY-----\n";
	}

	private KeyUtils() {}

}
