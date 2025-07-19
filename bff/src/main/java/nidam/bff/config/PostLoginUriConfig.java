package nidam.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.WebFilter;

import java.util.logging.Logger;

@Configuration
public class PostLoginUriConfig {

	private Logger log = Logger.getLogger(PostLoginUriConfig.class.getName());

	/**
	 * 1️⃣ This runs *before* Spring Security’s oauth2Login() kicks in
	 * and saves your SPA’s post_login_success_uri into the WebSession.
	 */
	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	public WebFilter postLoginSuccessUriFilter() {
		return (exchange, chain) -> {
			String redirectUri = exchange.getRequest()
					.getQueryParams()
					.getFirst("post_login_success_uri");
			log.info("post login redirectUri: " + redirectUri);
//			log.info("exchange.getRequest().getQueryParams(): " + exchange.getRequest().getQueryParams());
			if (redirectUri != null && redirectUri.startsWith("http")) {
				return exchange.getSession()
						.doOnNext(session -> session.getAttributes()
								.put("POST_LOGIN_SUCCESS_URI", redirectUri))
						.then(chain.filter(exchange));
			}

			return chain.filter(exchange);
		};
	}
}