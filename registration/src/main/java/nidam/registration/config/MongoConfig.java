package nidam.registration.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB configuration for the Nidam registration module. This class is retained for documentation purposes,
 * as Spring Boot auto-configuration already provides the required setup.
 *
 * <p>This configuration is activated when the {@code mongo} Spring profile is active.
 * It enables Spring Data MongoDB repositories located under
 * {@code nidam.registration.repositories.mongo}.
 *
 * <p>The active profile is controlled via:
 * <pre>
 * nidam.persistence-mode=mongo
 * spring.profiles.active=${nidam.persistence-mode}
 * </pre>
 *
 * <p>When active:
 * <ul>
 *   <li>Mongo repositories are enabled</li>
 *   <li>JPA auto-configuration is disabled (see application-mongo.yml)</li>
 *   <li>MongoDB is used as the persistence layer</li>
 * </ul>
 *
 * <p>This class is part of the persistence abstraction layer that allows switching
 * between SQL and MongoDB without changing the service or controller layers.
 */
@Configuration
@Profile("mongo")
@EnableMongoRepositories(basePackages = "nidam.registration.repositories.mongo")
public class MongoConfig {
}
