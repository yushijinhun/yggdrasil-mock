package org.to2mbn.yggdrasil.mockserver;

import java.net.InetSocketAddress;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

// TODO: once we have other ways to obtain remote address in webflux, remove this class
@Component
public class IpAttributeFilter implements WebFilter {

	// type: InetSocketAddress
	public static final String ATTR_REMOTE_ADDR = IpAttributeFilter.class.getCanonicalName() + ".remote_ip";

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		InetSocketAddress addr = exchange.getRequest().getRemoteAddress();
		if (addr != null) {
			exchange.getAttributes().put(ATTR_REMOTE_ADDR, addr);
		}
		return chain.filter(exchange);
	}
}
