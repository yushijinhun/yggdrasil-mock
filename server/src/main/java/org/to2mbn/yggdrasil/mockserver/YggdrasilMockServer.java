package org.to2mbn.yggdrasil.mockserver;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.text.MessageFormat.format;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static org.apache.commons.io.IOUtils.resourceToString;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
@ConfigurationProperties(prefix = "yggdrasil.core", ignoreUnknownFields = false)
@SpringBootApplication
public class YggdrasilMockServer {

	private List<String> skinDomains;
	private String url;
	private String serverName;

	@Bean
	public String publickeyPem() throws IOException {
		return resourceToString("publickey.pem", UTF_8, YggdrasilMockServer.class.getClassLoader());
	}

	@Bean
	public ServerMeta serverMeta(
			@Value("#{publickeyPem}") String publickeyPem,
			@Value("${build.version}") String buildVersion,
			@Value("${build.name}") String buildName,
			@Value("${git.commit.id}") String gitCommit) {
		ServerMeta meta = new ServerMeta();
		meta.setSignaturePublickey(publickeyPem);
		meta.setSkinDomains(skinDomains);
		meta.setMeta(ofEntries(
				entry("serverName", serverName),
				entry("implementationName", buildName),
				entry("implementationVersion", format("{0}-{1}", buildVersion, gitCommit.substring(0, 7)))));
		return meta;
	}

	@Bean
	public Supplier<UriBuilder> rootUrl() {
		return () -> UriComponentsBuilder.fromHttpUrl(url);
	}

	public List<String> getSkinDomains() {
		return skinDomains;
	}

	public void setSkinDomains(List<String> skinDomains) {
		this.skinDomains = skinDomains;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getServerName() {
		return serverName;
	}

	public void setServerName(String serverName) {
		this.serverName = serverName;
	}
}
