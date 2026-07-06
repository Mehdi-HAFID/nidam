package nidam.tokengenerator.redis.scheduled;

import nidam.tokengenerator.redis.entity.OAuth2AuthorizationGrantAuthorization;
import nidam.tokengenerator.redis.service.RedisOAuth2AuthorizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Scheduled cleanup task that removes stale entries from the global authorization
 * index {@code SET} key in Redis.
 *
 * <p>Active only when {@code nidam.session-mode=redis}.</p>
 *
 * <h3>The problem this solves</h3>
 * <p>Spring Data Redis maintains a global {@code SET} key at
 * {@code nidam:token-generator:oauth2:authorization} that tracks the IDs of all
 * persisted {@link OAuth2AuthorizationGrantAuthorization}
 * entities. When an entity's HASH key expires via Redis TTL, the corresponding ID
 * should be removed from this global SET via keyspace event notifications.
 * In practice this cleanup is unreliable — stale IDs accumulate in the SET
 * indefinitely after their HASH keys have expired.</p>
 *
 * <p>This task compensates by periodically scanning the global SET and removing
 * any IDs whose corresponding HASH key no longer exists in Redis.</p>
 *
 * <h3>Impact of stale entries</h3>
 * <p>Stale IDs in the global SET are functionally harmless — Spring Data Redis
 * returns {@code null} when it tries to read a HASH that no longer exists.
 * However they represent unbounded memory growth over time and should be
 * cleaned up to keep Redis storage predictable.</p>
 *
 * @see RedisOAuth2AuthorizationService
 */
@Component
@ConditionalOnProperty(name = "nidam.session-mode", havingValue = "redis")
public class GlobalSETCleanupTask {
	private final Logger log = Logger.getLogger(GlobalSETCleanupTask.class.getName());

	@Value("${spring.session.redis.namespace:nidam:token-generator}:oauth2:authorization")
	private String OAuth2RedisHash;

	private final StringRedisTemplate stringRedisTemplate;

	public GlobalSETCleanupTask(StringRedisTemplate stringRedisTemplate){
		this.stringRedisTemplate = stringRedisTemplate;
	}

	/**
	 * Scans the global authorization index {@code SET} and removes any member IDs
	 * whose corresponding HASH key no longer exists in Redis.
	 * <p>
	 * Runs every 1 hour. Each execution is O(n) where n is the number of
	 * members in the global SET — typically very small since active authorizations
	 * have a TTL of at most 12 hours.
	 */
	@Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS) // every 1 hour
	public void cleanupStaleAuthorizationIds() {
		Set<String> members = stringRedisTemplate.opsForSet().members(OAuth2RedisHash);
		if (members == null) return;
		for (String id : members) {
//			log.info("member id: " + id);
			Boolean exists = stringRedisTemplate.hasKey(OAuth2RedisHash + ":" + id);
//			log.info("member exists: " + exists);
			if (!exists) {
				log.info("removing member id: " + id);
				stringRedisTemplate.opsForSet().remove(OAuth2RedisHash, id);
			}
		}
	}
}
