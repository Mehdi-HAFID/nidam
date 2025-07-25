package nidam.bff.config;

import nidam.bff.config.properties.LoginProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.WebFilter;

import java.util.logging.Logger;

/**
 * Configuration class that registers a {@link WebFilter} to capture and store
 * the SPA-provided post-login redirect URI during OAuth2 login initiation.
 *
 * <p>This filter runs <strong>before</strong> Spring Security's {@code oauth2Login()} mechanism
 * is triggered. It activates only on requests to the login initiation endpoint
 * (e.g. {@code /oauth2/authorization/{registrationId}}), ensuring that it doesn't
 * run on unrelated routes.</p>
 *
 * <p>It looks for a query parameter (e.g. {@code post_login_success_uri}) whose name is
 * configurable, validates that it starts with one of the allowed redirect URI prefixes
 * (also configured), and saves it in the {@link org.springframework.web.server.WebSession}
 * under a configurable attribute key (e.g. {@code POST_LOGIN_SUCCESS_URI}).</p>
 *
 * <p>The stored value is later used by {@link nidam.bff.handler.LoginSuccessHandler}
 * to redirect the user to the intended frontend destination after a successful login.</p>
 *
 * <p>This setup allows frontend applications to initiate logins with a custom redirect
 * target while preserving strict control over acceptable destinations.</p>
 */
@Configuration
public class PostLoginRedirectCaptureConfig {

	private static final Logger log = Logger.getLogger(PostLoginRedirectCaptureConfig.class.getName());

	@Value("${react-uri}")
	private String defaultReactUri;

	public static final String BFF_LOGOUT_ENDPOINT = "/oauth2/authorization/token-generator";

	/*
	  1️⃣ This runs *before* Spring Security’s oauth2Login() kicks in
	  and saves your SPA’s post_login_success_uri into the WebSession. for the {@link nidam.bff.handler.LoginSuccessHandler}
	  to use upon successful redirection
	 */

	/**
	 * Registers the {@link WebFilter} that captures and stores a validated
	 * post-login redirect URI in the session for use after authentication.
	 *
	 * <p>This bean delegates configuration values such as the redirect parameter name,
	 * session attribute key, and allowed URI prefixes to {@link LoginProperties}.</p>
	 *
	 * @param loginProperties injected configuration for redirect capture
	 * @return a filter that stores the validated post-login redirect URI in session
	 */
	@Bean
	@Order(-200)
	public WebFilter postLoginSuccessUriFilter(LoginProperties loginProperties) {
		return (exchange, chain) -> {
			String path = exchange.getRequest().getURI().getPath();

			if (!path.startsWith(BFF_LOGOUT_ENDPOINT)) {
				log.info("skipped a 'post login redirectUri'");
				// Skip this filter if the path is not under /login
				return chain.filter(exchange);
			}

			String redirectUri = exchange.getRequest().getQueryParams().getFirst(loginProperties.getSuccessRedirectParamName());
			log.info("post login redirectUri: " + redirectUri);
			String resolvedUri = defaultReactUri; // fallback

			if (redirectUri != null && redirectUri.startsWith("http")) {
				boolean isAllowed = loginProperties.getAllowedRedirectUriPrefixes() != null
						&& loginProperties.getAllowedRedirectUriPrefixes().stream().anyMatch(redirectUri::startsWith);
				log.info("uri isAllowed: " + isAllowed);

				if (isAllowed) {
					resolvedUri = redirectUri;
				} else {
					log.warning("Rejected untrusted post_login_success_uri: " + redirectUri +
							". Allowed prefixes: " + loginProperties.getAllowedRedirectUriPrefixes());
				}
			}
			String finalSessionValue = resolvedUri;
			return exchange.getSession()
					.doOnNext(session -> session.getAttributes()
							.put(loginProperties.getSessionRedirectUriAttribute(), finalSessionValue))
					.then(chain.filter(exchange));
		};
	}
}