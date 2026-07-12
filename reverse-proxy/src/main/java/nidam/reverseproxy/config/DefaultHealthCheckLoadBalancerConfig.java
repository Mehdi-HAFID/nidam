package nidam.reverseproxy.config;

import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Generic Spring Cloud LoadBalancer configuration that enables health-check-aware
 * instance filtering for any named client.
 *
 * <p>This class is intentionally <b>not</b> annotated with {@code @Configuration}
 * and must not be in the component scan path. Spring Cloud LoadBalancer instantiates
 * it in an isolated child context per {@link org.springframework.cloud.client.loadbalancer.LoadBalancerClient @LoadBalancerClient}
 * entry that references it; placing it in the main application context would break
 * that isolation and cause unpredictable load balancer behavior.</p>
 *
 * <p>Configures a {@link ServiceInstanceListSupplier} that wraps the
 * {@link org.springframework.cloud.client.discovery.ReactiveDiscoveryClient ReactiveDiscoveryClient} with a health
 * check filter. Independently of request traffic, the supplier polls each registered
 * instance's health endpoint on a fixed schedule (governed by
 * {@code spring.cloud.loadbalancer.health-check.interval}, currently 5s) and caches
 * the resulting alive/dead status. Health check paths are resolved automatically per
 * service via {@code spring.cloud.loadbalancer.health-check.path.<service-name>} —
 * nothing hardcoded here. Routing decisions consult this cached status rather than
 * checking liveness at request time, so a downed instance is excluded from the pool
 * within one polling interval rather than continuing to receive traffic and producing
 * connection-refused errors.</p>
 *
 * <p>Reused across every entry in {@link LoadBalancerConfig}'s
 * {@code @LoadBalancerClients} — one class, one context per named client, no
 * per-service duplication needed.</p>
 *
 * @see LoadBalancerConfig
 */
public class DefaultHealthCheckLoadBalancerConfig {

	@Bean
	public ServiceInstanceListSupplier serviceInstanceListSupplier(ConfigurableApplicationContext context) {
		return ServiceInstanceListSupplier.builder()
				.withDiscoveryClient()
				.withHealthChecks()
				.build(context);
	}
}
