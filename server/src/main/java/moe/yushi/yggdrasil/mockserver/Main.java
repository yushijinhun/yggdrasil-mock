package moe.yushi.yggdrasil.mockserver;

import static java.text.MessageFormat.format;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

public final class Main {

	private static final String CONFIG_PATH = "./application.yaml";
	private static final String DEFAULT_CONFIG_PATH = "/default-application.yaml";

	private Main() {}

	public static void main(String[] args) {
		if (!doesConfigExist()) {
			try {
				copyDefaultConfigTo(Paths.get(CONFIG_PATH));
			} catch (IOException e) {
				System.err.println(format("Unable to copy default configuration to {0}: {1}", CONFIG_PATH, e));
				System.exit(1);
			}
			System.err.println(format("A new configuration has been written to {0}", CONFIG_PATH));
		}

		startApplication(args);
	}

	private static boolean doesConfigExist() {
		return Files.exists(Paths.get(CONFIG_PATH));
	}

	private static void copyDefaultConfigTo(Path target) throws IOException {
		try (var in = YggdrasilMockServer.class.getResourceAsStream(DEFAULT_CONFIG_PATH)) {
			if (in == null)
				throw new FileNotFoundException(DEFAULT_CONFIG_PATH);

			Files.copy(in, target);
		}
	}

	private static void startApplication(String[] args) {
		var app = new SpringApplication(YggdrasilMockServer.class);
		app.setWebApplicationType(WebApplicationType.REACTIVE);
		app.setDefaultProperties(getDefaultProperties());
		app.run(args);
	}

	private static Properties getDefaultProperties() {
		var properties = new Properties();
		tryLoadProperties("/git.properties", properties);
		tryLoadProperties("/META-INF/build-info.properties", properties);
		return properties;
	}

	private static void tryLoadProperties(String location, Properties properties) {
		try {
			loadProperties(location, properties);
		} catch (IOException e) {
			System.err.println(format("Unable to load properties {0}: {1}", location, e));
		}
	}

	private static void loadProperties(String location, Properties properties) throws IOException {
		try (var in = YggdrasilMockServer.class.getResourceAsStream(location)) {
			if (in == null)
				throw new FileNotFoundException(location);

			properties.load(in);
		}
	}
}
