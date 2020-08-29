package moe.yushi.yggdrasil_mock;

import static java.text.MessageFormat.format;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import org.springframework.boot.SpringApplication;

public final class Main {
	private Main() {}

	private static final String CONFIG_PATH = "./application.yaml";
	private static final String DEFAULT_CONFIG_PATH = "/default-application.yaml";

	public static void main(String[] args) {
		if (!Files.exists(Paths.get(CONFIG_PATH))) {
			try (var in = YggdrasilMockServer.class.getResourceAsStream(DEFAULT_CONFIG_PATH)) {
				if (in == null)
					throw new FileNotFoundException(DEFAULT_CONFIG_PATH);

				Files.copy(in, Paths.get(CONFIG_PATH));
			} catch (IOException e) {
				System.err.println(format("Unable to copy default configuration to {0}: {1}", CONFIG_PATH, e));
				System.exit(1);
			}
			System.err.println(format("A new configuration has been written to {0}", CONFIG_PATH));
		}

		var app = new SpringApplication(YggdrasilMockServer.class);
		app.setDefaultProperties(getDefaultProperties());
		app.setLogStartupInfo(false);
		app.run(args);
	}

	private static Properties getDefaultProperties() {
		var properties = new Properties();
		tryLoadProperties("/git.properties", properties);
		tryLoadProperties("/META-INF/build-info.properties", properties);
		return properties;
	}

	private static void tryLoadProperties(String location, Properties properties) {
		try (var in = YggdrasilMockServer.class.getResourceAsStream(location)) {
			if (in == null)
				throw new FileNotFoundException(location);

			properties.load(in);
		} catch (IOException e) {
			System.err.println(format("Unable to load properties {0}: {1}", location, e));
		}
	}
}
