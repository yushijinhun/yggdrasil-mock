package org.to2mbn.yggdrasil.mockserver;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static org.apache.commons.io.IOUtils.resourceToString;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SpringBootApplication
public class YggdrasilMockServer {

	public static void main(String[] args) throws Exception {
		SpringApplication.run(YggdrasilMockServer.class, args);
	}

	@Bean
	public String publickeyPem() throws IOException {
		return resourceToString("publickey.pem", UTF_8, YggdrasilMockServer.class.getClassLoader());
	}

	@Bean
	public ServerMeta serverMeta(@Value("#{publickeyPem}") String publickeyPem) {
		ServerMeta meta = new ServerMeta();
		meta.setSignaturePublickey(publickeyPem);
		meta.setSkinDomains(Arrays.asList(".to2mbn.org"));
		meta.setMeta(ofEntries(
				entry("serverName", "yggdrasil mock server"),
				entry("implementationName", "yggdrasil-mock-server"),
				entry("implementationVersion", "0.0.1")));
		return meta;
	}

}
