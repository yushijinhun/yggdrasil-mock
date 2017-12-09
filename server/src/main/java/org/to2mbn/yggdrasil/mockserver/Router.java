package org.to2mbn.yggdrasil.mockserver;

import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class Router {

	@Autowired
	private ServerMeta meta;

	@Bean
	public RouterFunction<ServerResponse> apiRouter() {
		return route(GET("/"),
				req -> ok()
						.contentType(APPLICATION_JSON_UTF8)
						.syncBody(meta));
	}

}
