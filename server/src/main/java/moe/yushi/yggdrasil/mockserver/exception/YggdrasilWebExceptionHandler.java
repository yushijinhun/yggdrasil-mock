package moe.yushi.yggdrasil.mockserver.exception;

import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.web.reactive.function.server.RequestPredicates.all;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import java.util.Map;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.autoconfigure.web.ResourceProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.DefaultErrorWebExceptionHandler;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class YggdrasilWebExceptionHandler extends DefaultErrorWebExceptionHandler {

	public YggdrasilWebExceptionHandler(ResourceProperties resourceProperties, ApplicationContext applicationContext, ServerCodecConfigurer serverCodecConfigurer) {
		super(new DefaultErrorAttributes() {

			@Override
			public Map<String, Object> getErrorAttributes(ServerRequest request, boolean includeStackTrace) {
				var result = super.getErrorAttributes(request, includeStackTrace);
				result.put("yggdrasil", getYggdrasilError(request));
				return result;
			}

			Map<String, Object> getYggdrasilError(ServerRequest request) {
				var error = getError(request);
				if (error instanceof YggdrasilException) {
					return ofEntries(
							entry("error", ((YggdrasilException) error).getYggdrasilError()),
							entry("errorMessage", ((YggdrasilException) error).getYggdrasilMessage()));
				} else {
					var errorStatus =
							error instanceof ResponseStatusException
									? ((ResponseStatusException) error).getStatus()
									: INTERNAL_SERVER_ERROR;
					return ofEntries(
							entry("error", errorStatus.getReasonPhrase()),
							entry("errorMessage", errorStatus.value() + " " + errorStatus.getReasonPhrase()));
				}
			}

		}, resourceProperties, new ErrorProperties(), applicationContext);
		setMessageReaders(serverCodecConfigurer.getReaders());
		setMessageWriters(serverCodecConfigurer.getWriters());
	}

	@Override
	protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
		return route(all(), req -> {
			var error = getErrorAttributes(req, false);
			return ServerResponse.status(getHttpStatus(error))
					.contentType(APPLICATION_JSON_UTF8)
					.body(BodyInserters.fromObject(error.get("yggdrasil")));
		});
	}
}
