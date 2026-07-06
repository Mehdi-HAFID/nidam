package nidam.reverseproxy.config;

import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a custom Spring Cloud LoadBalancer configuration for the
 * {@code token-generator} service.
 *
 * <p>Associates {@link TokenGeneratorLoadBalancerConfig} with the
 * {@code token-generator} service name, which corresponds to the
 * {@code lb://token-generator} URI used in the Spring Cloud Gateway route
 * definition. Spring Cloud LoadBalancer instantiates
 * {@link TokenGeneratorLoadBalancerConfig} in an isolated child context
 * separate from the main application context — this is a Spring Cloud
 * LoadBalancer requirement, which is why the two classes cannot be merged.</p>
 *
 * @see TokenGeneratorLoadBalancerConfig
 */
@Configuration
@LoadBalancerClient(name = "token-generator", configuration = TokenGeneratorLoadBalancerConfig.class)
public class LoadBalancerConfig {
}
