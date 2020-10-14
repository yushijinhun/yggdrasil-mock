package moe.yushi.yggdrasil_mock;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import moe.yushi.yggdrasil_mock.YggdrasilDatabase.TextureType;

@Configuration
@EnableWebFlux
public class WebConfig implements WebFluxConfigurer {

	public static class StringToTextureTypeConverter implements Converter<String, TextureType> {
		@Override
		public TextureType convert(String source) {
			return TextureType.valueOf(source.toUpperCase());
		}
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedMethods("*")
				.allowCredentials(false);
	}

	@Override
	public void addFormatters(FormatterRegistry registry) {
		registry.addConverter(new StringToTextureTypeConverter());
	}
}
