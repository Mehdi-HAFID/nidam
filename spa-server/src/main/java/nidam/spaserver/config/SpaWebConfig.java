package nidam.spaserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.logging.Logger;

@Configuration
public class SpaWebConfig {
	private final static Logger log = Logger.getLogger(SpaWebConfig.class.getName());

	// SPA_ROOT is the name of the folder where the build folder content are copied, to rename to 'spa'
	private static final String SPA_ROOT = "spa";

	@Value("${registration-uri}")
	private String registrationUri;

	@Value("${react-proxy-uri}")
	private String reactProxyUri;

	@Value("${resource-server-through-bff-proxy-uri}")
	private String resourceServerProxyUri;

	@Value("${react-login-url}")
	private String reactLoginUrl;

	@Value("${react-logout-url}")
	private String reactLogoutUrl;

	@Value("${react-prefix}")
	private String reactPrefix;

	@Bean
	public RouterFunction<ServerResponse> spaRouter() {
		FileSystemResource index = new FileSystemResource(SPA_ROOT + "/index.html");

		return RouterFunctions.route()
				.GET(reactPrefix + "/config.js", request -> {
					String js = """
                window.NIDAM_CONFIG = {
                	BACKEND_REGISTRATION_URL: "%s",
                	BASE_URI: "%s",
                	RESOURCE_SERVER_URI: "%s",
                	LOGIN_URL: "%s",
                	LOGOUT_URL: "%s",
                };
            """.formatted(registrationUri,
							reactProxyUri,
							resourceServerProxyUri,
							reactLoginUrl,
							reactLogoutUrl);
//					log.info("computed react config: " + js);

					return ServerResponse.ok()
							.header("Content-Type", "application/javascript")
							.bodyValue(js);
				})

				// Static assets
				.GET(reactPrefix + "/**", request -> {
					String path = request.path().replace(reactPrefix + "/", "");
					FileSystemResource resource = new FileSystemResource(SPA_ROOT + "/" + path);

					if (resource.exists() && resource.isReadable()) {
						return ServerResponse.ok().bodyValue(resource);
					}

					// SPA fallback
					return ServerResponse.ok().bodyValue(index);
				})
				.build();
	}
}
