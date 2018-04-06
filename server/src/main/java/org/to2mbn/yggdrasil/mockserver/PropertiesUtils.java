package org.to2mbn.yggdrasil.mockserver;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Map.ofEntries;
import static java.util.stream.Collectors.toList;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class PropertiesUtils {

	private static KeyPair keyPair;
	private static ObjectMapper objectMapper;

	static {
		keyPair = KeyUtils.generateKey();
		objectMapper = new ObjectMapper();
	}

	@SafeVarargs
	public static String base64Encoded(Entry<String, Object>... entries) {
		try {
			return Base64.getEncoder().encodeToString(
					objectMapper.writer().writeValueAsString(
							ofEntries(entries))
							.getBytes(UTF_8));
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	@SafeVarargs
	public static List<?> properties(Entry<String, String>... entries) {
		return properties(false, entries);
	}

	@SafeVarargs
	public static List<?> properties(boolean sign, Entry<String, String>... entries) {
		return Stream.of(entries)
				.map(entry -> {
					Map<String, String> property = new LinkedHashMap<>();
					property.put("name", entry.getKey());
					property.put("value", entry.getValue());
					if (sign) {
						property.put("signature", sign(entry.getValue()));
					}
					return property;
				})
				.collect(toList());
	}

	public static PublicKey getSignaturePublicKey() {
		return keyPair.getPublic();
	}

	private static String sign(String data) {
		try {
			Signature signature = Signature.getInstance("SHA1withRSA");
			signature.initSign(keyPair.getPrivate(), new SecureRandom());
			signature.update(data.getBytes(UTF_8));
			return Base64.getEncoder().encodeToString(signature.sign());
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	private PropertiesUtils() {}
}
