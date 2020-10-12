package moe.yushi.yggdrasil_mock;

import static java.util.Objects.requireNonNull;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriBuilder;
import com.google.common.collect.MapMaker;

public class Texture {

	public final String hash;
	public final byte[] data;
	public final String url;

	public Texture(String hash, byte[] data, String url) {
		this.hash = requireNonNull(hash);
		this.data = requireNonNull(data);
		this.url = requireNonNull(url);
	}

	public static String computeTextureHash(BufferedImage img) {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		int width = img.getWidth();
		int height = img.getHeight();
		byte[] buf = new byte[4096];

		putInt(buf, 0, width);
		putInt(buf, 4, height);
		int pos = 8;
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				putInt(buf, pos, img.getRGB(x, y));
				if (buf[pos + 0] == 0) {
					buf[pos + 1] = buf[pos + 2] = buf[pos + 3] = 0;
				}
				pos += 4;
				if (pos == buf.length) {
					pos = 0;
					digest.update(buf, 0, buf.length);
				}
			}
		}
		if (pos > 0) {
			digest.update(buf, 0, pos);
		}

		byte[] sha256 = digest.digest();
		return String.format("%0" + (sha256.length << 1) + "x", new BigInteger(1, sha256));
	}

	private static void putInt(byte[] array, int offset, int x) {
		array[offset + 0] = (byte) (x >> 24 & 0xff);
		array[offset + 1] = (byte) (x >> 16 & 0xff);
		array[offset + 2] = (byte) (x >> 8 & 0xff);
		array[offset + 3] = (byte) (x >> 0 & 0xff);
	}

	@Component
	public static class Storage {
		private @Value("#{rootUrl}") Supplier<UriBuilder> rootUrl;
		private @Autowired ApplicationContext ctx;

		private Map<String, Texture> textures = new MapMaker()
				.weakValues()
				.makeMap();

		public Optional<Texture> getTexture(String hash) {
			return Optional.ofNullable(textures.get(hash));
		}

		public Texture loadTexture(InputStream in) throws IOException {
			var img = ImageIO.read(in);
			var hash = computeTextureHash(img);

			var existent = textures.get(hash);
			if (existent != null) {
				return existent;
			}

			var url = rootUrl.get().path("/textures/{hash}").build(hash).toString();
			var buf = new ByteArrayOutputStream();
			ImageIO.write(img, "png", buf);
			var texture = new Texture(hash, buf.toByteArray(), url);

			existent = textures.putIfAbsent(hash, texture);

			if (existent != null) {
				return existent;
			}
			return texture;
		}

		public Texture loadTexture(String url) throws IOException {
			try (var in = ctx.getResource(url).getInputStream()) {
				return loadTexture(in);
			}
		}
	}
}
