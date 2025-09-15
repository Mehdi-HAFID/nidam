//package nidam.bff.config;
//
//import nidam.bff.config.properties.LoginProperties;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.annotation.Order;
//import org.springframework.web.server.WebFilter;
//
//import java.util.logging.Logger;
//
//@Configuration
//public class PostLogoutRedirectCaptureConfig {
//
//	private static final Logger log = Logger.getLogger(PostLogoutRedirectCaptureConfig.class.getName());
//
//	@Value("${react-uri}")
//	private String defaultReactUri;
//
//	public static final String BFF_LOGOUT_INIT_ENDPOINT = "/bff/logout";
//
//	@Bean
//	@Order(-200)
//	public WebFilter postLogoutSuccessUriFilter(LoginProperties loginProperties) {
//		return (exchange, chain) -> {
//			String path = exchange.getRequest().getURI().getPath();
//
//			if (!path.equals(BFF_LOGOUT_INIT_ENDPOINT)) {
//				log.info("skipped a 'post logout redirectUri'");
//				return chain.filter(exchange);
//			}
//
//			String redirectUri = exchange.getRequest().getQueryParams().getFirst("post_logout_success_uri");
//			log.info("post logout redirectUri: " + redirectUri);
//			String resolvedUri = defaultReactUri;
//
//			if (redirectUri != null && redirectUri.startsWith("http")) {
//				boolean isAllowed = loginProperties.getAllowedRedirectUriPrefixes() != null
//						&& loginProperties.getAllowedRedirectUriPrefixes().stream().anyMatch(redirectUri::startsWith);
//
//				if (isAllowed) {
//					resolvedUri = redirectUri;
//				} else {
//					log.warning("Rejected untrusted post_logout_success_uri: " + redirectUri +
//							". Allowed prefixes: " + loginProperties.getAllowedRedirectUriPrefixes());
//				}
//			}
//
//			String finalSessionValue = resolvedUri;
//			return exchange.getSession()
//					.doOnNext(session -> session.getAttributes()
//							.put("POST_LOGOUT_REDIRECT_URI", finalSessionValue))
//					.then(chain.filter(exchange));
//		};
//	}
//}
