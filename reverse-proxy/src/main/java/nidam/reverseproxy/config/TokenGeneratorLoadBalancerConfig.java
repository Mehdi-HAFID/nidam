package nidam.reverseproxy.config;

import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Spring Cloud LoadBalancer configuration for the {@code token-generator} service
 * that enables health-check-aware instance filtering.
 *
 * <p>This class is intentionally <b>not</b> annotated with {@code @Configuration}
 * and must not be in the component scan path. Spring Cloud LoadBalancer instantiates
 * it in an isolated child context via
 * {@link LoadBalancerConfig @LoadBalancerClient(configuration = ...)}. Placing it in
 * the main application context would break that isolation and cause unpredictable
 * load balancer behavior.</p>
 *
 * <p>Configures a {@link ServiceInstanceListSupplier} that wraps the
 * {@link org.springframework.cloud.client.discovery.DiscoveryClient} with a health
 * check filter. Before routing a request, the supplier calls the health endpoint of
 * each registered {@code token-generator} instance (configured via
 * {@code spring.cloud.loadbalancer.health-check.path.token-generator}) and excludes
 * any instance that does not return a healthy response. This ensures that a downed
 * instance is removed from the pool within one health check interval rather than
 * continuing to receive traffic and producing connection refused errors.</p>
 *
 * @see LoadBalancerConfig
 */
public class TokenGeneratorLoadBalancerConfig {

	@Bean
	public ServiceInstanceListSupplier serviceInstanceListSupplier(ConfigurableApplicationContext context) {
		return ServiceInstanceListSupplier.builder()
				.withDiscoveryClient()
				.withHealthChecks()
				.build(context);
	}
}
