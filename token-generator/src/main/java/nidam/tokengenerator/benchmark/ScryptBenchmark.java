package nidam.tokengenerator.benchmark;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

//@Component
public class ScryptBenchmark implements CommandLineRunner {
	private final Logger log = Logger.getLogger(ScryptBenchmark.class.getName());

	private final PasswordEncoder passwordEncoder;

	public ScryptBenchmark(PasswordEncoder passwordEncoder) {
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(String... args) throws Exception{
		String rawPassword = "benchmark-password";
		String encoded = passwordEncoder.encode(rawPassword);


		int cores = 4; // Runtime.getRuntime().availableProcessors(); // should be 8 on your box
		int iterations = 100; // per thread

//		int threads = 20;       // number of concurrent workers (simulate VUs)
//		int iterations = 200;   // total matches per thread

		ExecutorService pool = Executors.newFixedThreadPool(cores);
		List<Future<List<Long>>> futures = new ArrayList<>();

		for (int t = 0; t < cores; t++) {
			futures.add(pool.submit(() -> {
				List<Long> times = new ArrayList<>(iterations);
				for (int i = 0; i < iterations; i++) {
					long t0 = System.nanoTime();
					boolean ok = passwordEncoder.matches(rawPassword, encoded);
					long t1 = System.nanoTime();
					if (!ok) throw new IllegalStateException("Password check failed");
					times.add(t1 - t0);
				}
				return times;
			}));
		}

		pool.shutdown();
		pool.awaitTermination(10, TimeUnit.MINUTES);

		// Collect all timings
		List<Long> all = new ArrayList<>();
		for (Future<List<Long>> f : futures) {
			all.addAll(f.get());
		}

		// Stats
		double avgMs = all.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
		Collections.sort(all);
		double p50 = all.get((int)(all.size() * 0.50)) / 1_000_000.0;
		double p90 = all.get((int)(all.size() * 0.90)) / 1_000_000.0;
		double p95 = all.get((int)(all.size() * 0.95)) / 1_000_000.0;

		double totalSec = all.stream().mapToLong(Long::longValue).sum() / 1_000_000_000.0;
		double throughput = all.size() / totalSec;

		log.info("Scrypt throughput benchmark (cores="+cores+", iterations="+iterations+")");
		log.info("Avg: "+avgMs+" ms | p50: "+p50+" ms | p90: "+p90+" ms | p95: "+p95+" ms");
		log.info("Total verifications: "+ all.size() +" | Effective throughput: "+throughput+" matches/sec");
	}
}