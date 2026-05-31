package nidam.registration.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * SQL (JPA) configuration for the Nidam registration module. This class is retained for documentation purposes,
 * as Spring Boot auto-configuration already provides the required setup.
 *
 * <p>This configuration is activated when the {@code sql} Spring profile is active.
 * It enables Spring Data JPA repositories and scans SQL entity packages.
 *
 * <p>The active profile is controlled via:
 * <pre>
 * nidam.persistence-mode=sql
 * spring.profiles.active=${nidam.persistence-mode}
 * </pre>
 *
 * <p>When active:
 * <ul>
 *   <li>JPA repositories are enabled</li>
 *   <li>Entity classes under {@code nidam.registration.entities.sql} are scanned</li>
 *   <li>Mongo auto-configuration is disabled (see application-sql.yml)</li>
 * </ul>
 *
 * <p>This configuration works together with Spring Boot JPA properties such as:
 * <ul>
 *   <li>{@code spring.jpa.generate-ddl=true}</li>
 *   <li>{@code spring.sql.init.mode=always}</li>
 * </ul>
 *
 * <p>Part of the persistence abstraction layer allowing interchangeable SQL and MongoDB backends.
 */
@Configuration
@Profile("sql")
@EnableJpaRepositories(basePackages = "nidam.registration.repositories.sql")
@EntityScan(basePackages = "nidam.registration.entities.sql")
public class SqlConfig {
}
