//package nidam.bff.controller;
//
//import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties;
//import jakarta.validation.constraints.NotEmpty;
//import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
//import org.springframework.http.MediaType;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RestController;
//import reactor.core.publisher.Mono;
//
//import java.net.URI;
//import java.net.URISyntaxException;
//import java.util.List;
//import java.util.Objects;
//import java.util.logging.Logger;
//
//
//// TODO for now just follow tutorial, to be removed.
//@RestController
//public class LoginOptionsController {
//
//	private final Logger log = Logger.getLogger(LoginOptionsController.class.getName());
//
//	private final List<LoginOptionDto> loginOptions;
//
//	public LoginOptionsController(OAuth2ClientProperties clientProps, SpringAddonsOidcProperties addonsProperties) {
//		final String clientAuthority = addonsProperties.getClient()
//				.getClientUri()
//				.getAuthority();
//
//		this.loginOptions = clientProps.getRegistration()
//				.entrySet()
//				.stream()
//				.filter(e -> "authorization_code".equals(e.getValue().getAuthorizationGrantType()))
//				.map(e -> {
//					final String label = e.getValue().getProvider();
//					final String loginUri = "%s/oauth2/authorization/%s".formatted(addonsProperties.getClient().getClientUri(), e.getKey());
//					final String providerId = clientProps.getRegistration()
//							.get(e.getKey())
//							.getProvider();
//					final String providerIssuerAuthority = URI.create(clientProps.getProvider()
//									.get(providerId)
//									.getIssuerUri())
//							.getAuthority();
//					log.info("LoginOptionDto: " + "label: " + label +", loginUri: " + loginUri +
//							", clientAuthority: " + clientAuthority + ", providerIssuerAuthority: " + providerIssuerAuthority);
//					return new LoginOptionDto(label, loginUri, Objects.equals(clientAuthority, providerIssuerAuthority));
//				})
//				.toList();
//	}
//
//	@GetMapping(path = "/login-options", produces = MediaType.APPLICATION_JSON_VALUE)
//	public Mono<List<LoginOptionDto>> getLoginOptions() throws URISyntaxException {
//		return Mono.just(this.loginOptions);
//	}
//
//	public static record LoginOptionDto(@NotEmpty String label, @NotEmpty String loginUri, boolean isSameAuthority) {
//	}
//
//}