package nidam.spaserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class SpaWebConfig {
	private static final String SPA_ROOT = "react-ui";

	@Bean
	RouterFunction<ServerResponse> spaRouter() {
		FileSystemResource index = new FileSystemResource(SPA_ROOT + "/index.html");

		return RouterFunctions.route()
				// Static assets
				.GET("/react-ui/**", request -> {
					String path = request.path().replace("/react-ui/", "");
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
