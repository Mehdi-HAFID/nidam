package nidam.tokengenerator.benchmark;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

// On CPUs with SMT, Task Manager shows ~50% utilization when all physical cores are saturated. For scrypt, hyper‑threads don’t add throughput,
// so the effective ceiling is ~18 verifications/sec on our 8‑core Ryzen 5800H.
// This is the closest password matching benchmark to k6 benchmark. with int threads = 8, I get Throughput: 17.90 matches/sec
// Avg: 443.419349375 ms | p50: 446.3129 ms | p90: 493.1986 ms | p95: 503.3984 ms | p99: 516.203 ms
// Ignore the 50–60% task manager overall CPU metric — it’s misleading on hyper‑threaded CPUs.
// scrypt’s scaling behavior: one thread per physical core is optimal, and SMT doesn’t help.
// k6 is 10.55/s for the whole login flow, with each taking 4 seconds to complete

/**
 * Benchmark runner for measuring wall‑clock throughput and latency
 * of password verification using a {@link PasswordEncoder} (e.g. scrypt).
 *
 * <p>Runs concurrent tasks to simulate multiple threads performing
 * password matches, then logs throughput and latency statistics
 * (avg, p50, p90, p95, p99).</p>
 *
 * Usage: executes automatically at startup via {@link CommandLineRunner}.
 */
//@Component
public class ScryptWallClockBenchmark implements CommandLineRunner {
	private final Logger log = Logger.getLogger(ScryptWallClockBenchmark.class.getName());

	private final PasswordEncoder passwordEncoder;

	public ScryptWallClockBenchmark(PasswordEncoder passwordEncoder) {
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(String... args) throws Exception{
		final String rawPassword = "benchmark-password";
		final String encoded = passwordEncoder.encode(rawPassword);

		final int threads = 8;
		final int iterationsPerThread = 100; // adjust for run length

		ExecutorService pool = Executors.newFixedThreadPool(threads);
		List<Future<List<Long>>> futures = new ArrayList<>(threads);
		CountDownLatch startGate = new CountDownLatch(1);
		AtomicInteger completed = new AtomicInteger(0);

		// Prepare tasks
		for (int t = 0; t < threads; t++) {
			futures.add(pool.submit(() -> {
				List<Long> latenciesNs = new ArrayList<>(iterationsPerThread);
				// Wait for coordinated start
				startGate.await();
				for (int i = 0; i < iterationsPerThread; i++) {
					long t0 = System.nanoTime();
					boolean ok = passwordEncoder.matches(rawPassword, encoded);
					long t1 = System.nanoTime();
					if (!ok) throw new IllegalStateException("Password check failed");
					latenciesNs.add(t1 - t0);
					completed.incrementAndGet();
				}
				return latenciesNs;
			}));
		}

		// Wall‑clock timing
		long wallStart = System.nanoTime();
		startGate.countDown(); // release all threads

		// Collect results
		List<Long> allLatencies = new ArrayList<>(threads * iterationsPerThread);
		for (Future<List<Long>> f : futures) {
			allLatencies.addAll(f.get());
		}
		pool.shutdown();
		pool.awaitTermination(5, TimeUnit.MINUTES);
		long wallEnd = System.nanoTime();

		// Throughput based on wall‑clock
		double wallSeconds = (wallEnd - wallStart) / 1_000_000_000.0;
		int total = completed.get();
		double matchesPerSec = total / wallSeconds;

		// Latency stats
		double avgMs = allLatencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
		Collections.sort(allLatencies);
		double p50 = pct(allLatencies, 0.50) / 1_000_000.0;
		double p90 = pct(allLatencies, 0.90) / 1_000_000.0;
		double p95 = pct(allLatencies, 0.95) / 1_000_000.0;
		double p99 = pct(allLatencies, 0.99) / 1_000_000.0;

		log.info("Scrypt wall‑clock benchmark (threads="+threads+", iterations/thread="+iterationsPerThread+")");
		log.info("Total verifications: "+total+" | Wall‑clock: "+wallSeconds+" s | Throughput: "+matchesPerSec+" matches/sec");
		log.info("Avg: "+avgMs+" ms | p50: "+p50+" ms | p90: "+p90+" ms | p95: "+p95+" ms | p99: "+p99+" ms");
	}

	private static long pct(List<Long> sorted, double p) {
		int idx = Math.min(sorted.size() - 1, Math.max(0, (int) Math.floor(sorted.size() * p)));
		return sorted.get(idx);
	}
}