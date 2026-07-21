//package nidam.bff.security;
//
//import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
//import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
//import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
//import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
//import org.springframework.web.server.ServerWebExchange;
//import reactor.core.publisher.Mono;
//
//import java.util.Map;
//import java.util.Optional;
//import java.util.concurrent.ConcurrentHashMap;
//
//// TODO kept for future reference, race condition is fix in the SPA side by making sure /bff/me is only called once using React useRef() Effect.
//public class SessionCoalescingAuthorizedClientManager implements ReactiveOAuth2AuthorizedClientManager {
//
//	private final ReactiveOAuth2AuthorizedClientManager delegate;
//
//	// Same-instance-only coalescing key: session id -> in-flight (or cached-completed) authorize() call.
//	// Holds no authoritative data - Redis remains the only source of truth. Worst case on a cache miss
//	// (map empty after restart, or the two requests land on different instances) is today's behavior,
//	// not corruption.
//	private final Map<String, Mono<OAuth2AuthorizedClient>> inFlight = new ConcurrentHashMap<>();
//
//	public SessionCoalescingAuthorizedClientManager(ReactiveOAuth2AuthorizedClientManager delegate) {
//		this.delegate = delegate;
//	}
//
//	@Override
//	public Mono<OAuth2AuthorizedClient> authorize(OAuth2AuthorizeRequest request) {
//		return Mono.deferContextual(contextView -> {
//			Optional<ServerWebExchange> exchangeOpt = ServerWebExchangeContextFilter.getExchange(contextView);
//			if (exchangeOpt.isEmpty()) {
//				// No exchange to key on - can't coalesce, fall back to a direct (uncoalesced) call.
//				return delegate.authorize(request);
//			}
//			return exchangeOpt.get().getSession().flatMap(session -> {
//				String sessionId = session.getId();
//				// computeIfAbsent is atomic on the map: only the first concurrent caller for this
//				// sessionId actually invokes delegate.authorize() - which runs our provider's rotation
//				// AND the repository's own saveAuthorizedClient() as ONE unit. Any other caller sharing
//				// this sessionId gets the SAME Mono and replays its result once it completes; it never
//				// runs its own copy of either save, so neither save site can race against the other.
//				return inFlight.computeIfAbsent(sessionId, id ->
//						delegate.authorize(request)
//								.cache()
//								.doFinally(signal -> inFlight.remove(id)));
//			});
//		});
//	}
//}
