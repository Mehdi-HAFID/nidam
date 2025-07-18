//package nidam.bff.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
//import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
//import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
//
//@Configuration
//public class OAuth2LoginConfig {
//
//	@Bean
//	public ServerOAuth2AuthorizationRequestResolver customAuthorizationRequestResolver(
//			ReactiveClientRegistrationRepository repo
//	) {
//		return (exchange, clientRegistrationId) ->
//				repo.findByRegistrationId(clientRegistrationId)
//						.map(clientRegistration -> {
//							String redirectUri = exchange.getRequest()
//									.getQueryParams()
//									.getFirst("post_login_success_uri");
//
//							OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest
//									.authorizationCode()
//									.authorizationUri(clientRegistration.getProviderDetails().getAuthorizationUri())
//									.clientId(clientRegistration.getClientId())
//									.redirectUri(clientRegistration.getRedirectUri())
//									.scopes(clientRegistration.getScopes())
//									.state(redirectUri) // put post-login URI in state
//									.additionalParameters(params -> params.put("nonce", "abc123")) // optional
//									.build()
//									.toBuilder();
//
//							return builder.build();
//						});
//	}
//
//	@Bean
//	public WebSessionServerOAuth2AuthorizationRequestRepository authorizationRequestRepository() {
//		return new WebSessionServerOAuth2AuthorizationRequestRepository();
//	}
//
//}
