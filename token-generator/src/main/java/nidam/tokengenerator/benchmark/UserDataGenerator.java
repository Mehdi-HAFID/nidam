package nidam.tokengenerator.benchmark;

import nidam.tokengenerator.entities.Authority;
import nidam.tokengenerator.repositories.AuthorityRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;

// Generate 1000 users account
// Inserted 1000 users.
// Took 62715 ms (15.95 users/sec).
// Inserted 1000 users.
// Took 57953 ms (17.26 users/sec). this uses saveAll() and yaml batch_size: 100 & order_inserts: true
/**
 * Initializes demo user data at application startup.
 *
 * <p>Creates default authorities if none exist, then inserts a
 * configurable number of users concurrently in batches using
 * {@link UserInitService}. Logs total inserted users and throughput.</p>
 *
 * Usage: executed via {@link CommandLineRunner} bean.
 */
@Component
public class UserDataGenerator {

	private final Logger log = Logger.getLogger(UserDataGenerator.class.getName());

//	@Bean				// uncomment to generate users
	public CommandLineRunner initUsers(UserInitService userInitService, AuthorityRepository authRepo) {
		return args -> {
			final int totalUsers = 1_000_000;
			final int batchSize = 120_500;
			final int threads = 8;
			final int taskCount = (int) Math.ceil(totalUsers / (double) batchSize);
			log.info("Persisting " + totalUsers + " users ...");
			if (authRepo.count() == 0) {
				authRepo.save(new Authority("manage_users"));
				authRepo.save(new Authority("manage-projects"));
			}
			List<Authority> authorities = authRepo.findAll();

			long start = System.currentTimeMillis();

			ExecutorService pool = Executors.newFixedThreadPool(threads);
			List<Future<?>> futures = new ArrayList<>(taskCount);

			try {
				for (int t = 0; t < taskCount; t++) {
					final int startIdx = t * batchSize + 1;							// 1	101 ..
					final int endIdx = Math.min((t + 1) * batchSize, totalUsers);	// 100	200 ..

					// submit a runnable that calls the Spring bean method (which should be @Transactional)
					futures.add(pool.submit(() -> {
						// call the service method on the injected bean (runs with Spring proxy -> transaction)
						userInitService.insertUsersRange(startIdx, endIdx, authorities);
					}));
				}

				// wait for all tasks to finish and rethrow exceptions if any
				for (Future<?> f : futures) {
					try {
						f.get(); // blocks until the task completes; will throw ExecutionException if task threw
					} catch (ExecutionException ee) {
						// unwrap and rethrow as runtime to stop startup, optionally log more details
						throw new RuntimeException("User init task failed", ee.getCause());
					}
				}

			} finally {
				// graceful shutdown of pool
				pool.shutdown();
				if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
					pool.shutdownNow();
				}
			}

			long end = System.currentTimeMillis();
			long durationMs = end - start;
			double usersPerSec = (totalUsers * 1000.0) / Math.max(durationMs, 1);

			log.info("Inserted " + totalUsers + " users.");
			log.info("Took " + durationMs + " ms (" + String.format("%.2f", usersPerSec) + " users/sec).");


		};
	}
}
