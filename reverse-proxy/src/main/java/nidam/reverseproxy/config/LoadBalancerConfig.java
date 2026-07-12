package nidam.reverseproxy.config;

import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Configuration;

/**
 * Registers Spring Cloud LoadBalancer configuration for each load-balanced
 * downstream service the reverse proxy routes to.
 *
 * <p>Each {@link LoadBalancerClient @LoadBalancerClient} entry associates
 * {@link DefaultHealthCheckLoadBalancerConfig} with a service name matching the
 * {@code lb://<service-name>} URI used in that service's Spring Cloud Gateway route
 * definition — currently {@code token-generator} and {@code bff}. Spring Cloud
 * LoadBalancer instantiates {@link DefaultHealthCheckLoadBalancerConfig} in its own
 * isolated child context per entry, separate from the main application context and
 * from each other — this is a Spring Cloud LoadBalancer requirement, which is why
 * the same configuration class is reused rather than merged into one shared bean.</p>
 *
 * <p>Adding load balancing for a new downstream service means adding another
 * {@code @LoadBalancerClient} entry here (reusing
 * {@link DefaultHealthCheckLoadBalancerConfig}) plus a matching
 * {@code spring.cloud.loadbalancer.health-check.path.<service-name>} entry in
 * configuration — no new Java class required.</p>
 *
 * @see DefaultHealthCheckLoadBalancerConfig
 */
@Configuration
@LoadBalancerClients({
		@LoadBalancerClient( name = "token-generator", configuration = DefaultHealthCheckLoadBalancerConfig.class),
		@LoadBalancerClient( name = "bff", configuration = DefaultHealthCheckLoadBalancerConfig.class)
})
public class LoadBalancerConfig {
}
