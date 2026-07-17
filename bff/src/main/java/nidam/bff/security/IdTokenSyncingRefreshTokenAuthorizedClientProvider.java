package nidam.bff.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.ReactiveOAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.oidc.authentication.ReactiveOidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.util.Assert;
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;

/**
 * Refresh-token {@link ReactiveOAuth2AuthorizedClientProvider} that keeps the BFF's
 * reactive {@code SecurityContext} in sync with the ID Token our Authorization Server
 * reissues on every {@code refresh_token} grant, and rotates the session id on every
 * * such refresh as a session-fixation defense.
 *
 * <h2>The problem this solves</h2>
 * <p>Spring's OAuth2 client support treats two things as unrelated: the
 * {@link OAuth2AuthorizedClient} (access token + refresh token, refreshed transparently
 * whenever the access token expires) and the {@code Authentication}/{@link OidcUser}
 * sitting in the {@code SecurityContext} (built once, at login, and never touched again
 * by the refresh machinery). The stock {@code RefreshTokenReactiveOAuth2AuthorizedClientProvider}
 * updates only the former. That's fine as long as the ID Token never changes after
 * login — but our Authorization Server reissues a brand-new ID Token on every refresh
 * (standard behavior whenever the {@code openid} scope is present, regardless of grant
 * type), and its {@code OAuth2AuthorizationService} only recognizes the
 * <em>latest</em> one. {@code OidcLogoutAuthenticationProvider} looks up the
 * {@code id_token_hint} via {@code authorizationService.findByToken(idTokenHint, ID_TOKEN)}
 * — if the BFF sends the token cached at login (now superseded), that lookup in the Authorization Server returns
 * {@code null} and RP-initiated logout fails with {@code invalid_token}.</p>
 *
 * <p>This is a known, acknowledged gap in Spring Security — see
 * spring-projects/spring-security#15509 and #16253 — eventually addressed for the
 * imperative Servlet stack via {@code OidcAuthorizedClientRefreshedEventListener}
 * (6.5+). No reactive/WebFlux equivalent is confirmed to exist, hence this class.</p>
 *
 * <h2>What this class does</h2>
 * <p>Drop-in replacement for the stock refresh provider — same contract (scope
 * passthrough via {@code REQUEST_SCOPE_ATTRIBUTE_NAME}, error mapping to
 * {@link ClientAuthorizationException}) — plus two additions on a successful refresh:
 * if the token response carried a new {@code id_token} (see
 * {@link #syncIdTokenIfPresent}), it decodes it, rebuilds the {@link OidcUser}, and
 * persists the updated {@code SecurityContext} back into the session; then, regardless
 * of whether an id_token was present, it rotates the session id via
 * {@code WebSession#changeSessionId()} (see {@link #authorize}) so a stolen session
 * identifier has a shrinking window of usefulness rather than surviving indefinitely.</p>
 *
 * <h2>How this collaborates with the rest of the wiring</h2>
 * <ul>
 *   <li>Must be registered via {@code .provider(...)} — not {@code .refreshToken()} —
 *   in the {@link ReactiveOAuth2AuthorizedClientProviderBuilder} chain used to build
 *   the {@code authorizedClientManager} bean, replacing the stock provider outright
 *   rather than running alongside it.</li>
 *   <li>Depends on {@link ServerWebExchangeContextFilter} being registered as an
 *   early-ordered {@code WebFilter} bean. {@code TokenRelayGatewayFilterFactory} does
 *   not thread the current {@link ServerWebExchange} through
 *   {@code OAuth2AuthorizationContext} attributes the way
 *   {@code ServerOAuth2AuthorizedClientExchangeFilterFunction} does for plain
 *   {@code WebClient} usage — confirmed empirically, not assumed — so the exchange has
 *   to be recovered from Reactor {@code Context} instead; see
 *   {@link #syncIdTokenIfPresent}.</li>
 *   <li>Writes through whatever {@link ServerSecurityContextRepository} it's
 *   constructed with. As long as that repository is a
 *   {@code WebSessionServerSecurityContextRepository} (Spring Security's own default),
 *   the write lands under the same {@code "SPRING_SECURITY_CONTEXT"} WebSession
 *   attribute key the rest of the app already reads — no separate integration point,
 *   no new session attribute to introduce. The session-id rotation in {@link #authorize}
 *   then eagerly persists that write under the new id as part of rotating — Spring
 *   Session's {@code changeSessionId()} calls {@code save()} internally — so for the
 *   refresh path specifically, that persistence no longer depends on Gateway's
 *   {@code [SaveSession]} filter running afterward; that filter still does its normal
 *   job for every other session write elsewhere in the app, just isn't load-bearing
 *   for this one anymore.</li>
 * </ul>
 */
public class IdTokenSyncingRefreshTokenAuthorizedClientProvider implements ReactiveOAuth2AuthorizedClientProvider {

	private static final Logger log = Logger.getLogger(IdTokenSyncingRefreshTokenAuthorizedClientProvider.class.getName());

	// Performs the actual refresh_token grant HTTP call to the AS's token endpoint - same client type the stock provider uses.
	private final ReactiveOAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> accessTokenResponseClient =
			new WebClientReactiveRefreshTokenTokenResponseClient();

	// Builds a per-ClientRegistration ReactiveJwtDecoder (issuer/audience/signature validation against the AS's JWKS) -
	// the same validation machinery used to decode ID Tokens at login time, reused here for the refreshed one.
	private final ReactiveOidcIdTokenDecoderFactory idTokenDecoderFactory = new ReactiveOidcIdTokenDecoderFactory();

	// Where the rebuilt SecurityContext gets persisted; supplied by the caller so this class stays agnostic
	// to whether the app is running in Redis-backed or in-memory WebSession mode.
	private final ServerSecurityContextRepository securityContextRepository;

	// How much slack to give the access token's expiry check, matching the stock provider's default and semantics.
	private Duration clockSkew = Duration.ofSeconds(60);

	// Injectable clock (rather than Instant.now()) so expiry checks are testable with a fixed time.
	private final Clock clock = Clock.systemUTC();

	/**
	 * @param securityContextRepository repository the refreshed {@code SecurityContext} is written to;
	 *                                  must match the repository the rest of the security filter chain
	 *                                  reads from, or the sync will be invisible to subsequent requests
	 */
	public IdTokenSyncingRefreshTokenAuthorizedClientProvider(ServerSecurityContextRepository securityContextRepository) {
		Assert.notNull(securityContextRepository, "securityContextRepository cannot be null");
		this.securityContextRepository = securityContextRepository;
	}

	/**
	 * Refreshes the given {@link OAuth2AuthorizedClient} if its access token has expired
	 * and a refresh token is available. On a successful refresh, also synchronizes any
	 * reissued ID Token into the session (see {@link #syncIdTokenIfPresent}) and rotates
	 * the session id - a new session identifier is issued on every token refresh, not
	 * just at login, as a defense against session fixation - before completing.
	 *
	 * @param context authorization-specific state for the client being (re)authorized;
	 *                supplies the current {@code Authentication}, {@link ClientRegistration},
	 *                and any previously authorized client
	 * @return the refreshed {@link OAuth2AuthorizedClient}, or an empty {@code Mono} if
	 * this provider doesn't apply (no authorized client yet, no refresh token, or access
	 * token not actually expired) — matching the stock provider's contract exactly, so
	 * the surrounding {@code DelegatingReactiveOAuth2AuthorizedClientProvider} chain
	 * behaves identically to before this class existed
	 */
	@Override
	public Mono<OAuth2AuthorizedClient> authorize(OAuth2AuthorizationContext context) {
		Assert.notNull(context, "context cannot be null");

		// Pull whatever client was previously authorized (if any) out of the context.
		OAuth2AuthorizedClient authorizedClient = context.getAuthorizedClient();

		// Bail out (empty Mono, not an error) if there's nothing to refresh: no prior client,
		// no refresh token on it, or the access token isn't actually expired yet. Returning
		// empty lets other providers in the delegating chain get a turn instead.
		if (authorizedClient == null || authorizedClient.getRefreshToken() == null
				|| !hasTokenExpired(authorizedClient.getAccessToken())) {
			return Mono.empty();
		}

		// Check whether the caller requested specific scopes for this authorization via the
		// well-known context attribute - preserved from the stock provider for parity, even
		// though nothing in our TokenRelay path currently sets this attribute.
		Object requestScope = context.getAttribute(OAuth2AuthorizationContext.REQUEST_SCOPE_ATTRIBUTE_NAME);
		Set<String> scopes = Collections.emptySet();
		if (requestScope != null) {
			Assert.isInstanceOf(String[].class, requestScope, "The context attribute must be of type String[] '"
					+ OAuth2AuthorizationContext.REQUEST_SCOPE_ATTRIBUTE_NAME + "'");
			scopes = new HashSet<>(Arrays.asList((String[]) requestScope));
		}

		// The registration (client-id, token endpoint URI, etc.) this refresh is being performed for.
		ClientRegistration clientRegistration = context.getClientRegistration();

		// Assemble the refresh_token grant request: current (expired) access token, the refresh
		// token that authorizes renewing it, and any requested scope narrowing.
		OAuth2RefreshTokenGrantRequest grantRequest = new OAuth2RefreshTokenGrantRequest(
				clientRegistration, authorizedClient.getAccessToken(), authorizedClient.getRefreshToken(), scopes);

		return Mono.just(grantRequest)
				// Perform the actual HTTP POST to the token endpoint and get back the raw token response.
				.flatMap(this.accessTokenResponseClient::getTokenResponse)
				// If the AS rejects the refresh (e.g. invalid_grant on a revoked/expired refresh token),
				// wrap it the same way the stock provider does so downstream re-auth-triggering logic
				// (anything pattern-matching on ClientAuthorizationException) keeps working unchanged.
				.onErrorMap(OAuth2AuthorizationException.class,
						(e) -> new ClientAuthorizationException(e.getError(), clientRegistration.getRegistrationId(), e))

				// The ServerWebExchange isn't available as a synchronous OAuth2AuthorizationContext
				// attribute here (TokenRelayGatewayFilterFactory doesn't populate it, unlike
				// ServerOAuth2AuthorizedClientExchangeFilterFunction's WebClient path) - deferContextual
				// delays evaluation until subscription time, when Reactor Context (populated by
				// ServerWebExchangeContextFilter further up the filter chain) is actually available.
				// Resolved once here and reused below for both the id-token sync and the rotation,
				// instead of doing this lookup twice.
				.flatMap(tokenResponse -> Mono.deferContextual(contextView -> {
					// Recover the current exchange from Reactor Context via the well-known accessor.
					Optional<ServerWebExchange> exchangeOpt = ServerWebExchangeContextFilter.getExchange(contextView);

					// If it's missing, the filter isn't registered, or isn't ordered early enough to wrap
					// this call path - log loudly enough to be noticed rather than fail the refresh itself.
					if (exchangeOpt.isEmpty()) {
						log.warning("No ServerWebExchange in Reactor Context - skipping id-token sync and session rotation");
						return Mono.just(tokenResponse);
					}

					ServerWebExchange exchange = exchangeOpt.get();

					// Write the freshened SecurityContext onto the session object first - changeSessionId()
					// persists whatever's on the session at the moment it runs, so this has to happen
					// before rotation, not after.
					return syncIdTokenIfPresent(context, clientRegistration, tokenResponse, exchange)
							// Re-fetch the WebSession - same memoized instance exchange.getSession() always
							// returns for this exchange - now that its attributes include the synced context.
							.then(exchange.getSession())
							// Rotate the session id; changeSessionId() eagerly saves the session under the
							// new id as part of this call, sweeping up the sync above along with it.
							.flatMap(WebSession::changeSessionId)
							// Sync and rotation both done - hand the original tokenResponse back downstream.
							.thenReturn(tokenResponse);
				}))
				// Build the new OAuth2AuthorizedClient from the refreshed access/refresh tokens -
				// identical to what the stock provider returns.
				.map((tokenResponse) -> new OAuth2AuthorizedClient(clientRegistration, context.getPrincipal().getName(),
						tokenResponse.getAccessToken(), tokenResponse.getRefreshToken()));
	}

	/**
	 * If the refresh response carried a new {@code id_token}, decode it and persist an
	 * updated {@link OidcUser} into the session. No-ops (with a log line) if the response
	 * has no id_token, the principal isn't an {@link OidcUser}, or the exchange isn't
	 * reachable from the context — nothing to sync in any of those cases.
	 */
	/**
	 * Detects a reissued {@code id_token} in the refresh response and, if present,
	 * rebuilds and persists an updated {@link OidcUser} so the session reflects the ID
	 * Token the Authorization Server currently considers valid.
	 *
	 * <p>No-ops (with an explanatory log line) in every case where syncing isn't
	 * possible or applicable: no id_token in the response (most refresh responses -
	 * this is AS-specific behavior, not guaranteed by spec), a principal that isn't an
	 * {@link OidcUser} (non-OIDC registrations), Resolving the {@link ServerWebExchange}
	 * itself is the caller's responsibility - see {@link #authorize} - since it's needed
	 * for session rotation too, not just this sync.</p>
	 *
	 * <p>When syncing does happen, it rebuilds <strong>three</strong> separate places
	 * the ID Token is duplicated — {@link DefaultOidcUser#getIdToken()},
	 * the {@link OidcUserAuthority} embedded in the principal's authorities, and the
	 * {@link OAuth2AuthenticationToken}'s own independent authorities list — from a
	 * single freshly-built {@link OidcUserAuthority}. Updating only the first (the
	 * obvious one) leaves the other two silently stale, since none of
	 * {@link DefaultOidcUser}'s constructors reconcile {@code idToken} against
	 * whatever {@code authorities} collection they're handed.</p>
	 *
	 * @param context            the authorization context, used to read the current
	 *                           {@code Authentication}/principal being refreshed
	 * @param clientRegistration the registration whose issuer/JWKS the new id_token
	 *                           must be validated against
	 * @param tokenResponse      the raw response from the refresh_token grant call
	 * @param exchange           the current exchange, resolved once by the caller from
	 *                           Reactor Context and passed in here so this method doesn't
	 *                           need its own {@code deferContextual} lookup
	 * @return a {@code Mono<Void>} completing once the sync (or the decision to skip
	 * it) is finished
	 */
	private Mono<Void> syncIdTokenIfPresent(OAuth2AuthorizationContext context, ClientRegistration clientRegistration,
	                                        OAuth2AccessTokenResponse tokenResponse, ServerWebExchange exchange) {
		log.info("tokenResponse: " + tokenResponse);

		// Spring's token-response parsing only maps well-known fields (access_token, expires_in,
		// refresh_token, scope) into typed properties; anything else the AS returns - including
		// id_token on a refresh grant, which isn't a typed field on OAuth2AccessTokenResponse -
		// lands in additionalParameters as a raw value.
		Object rawIdToken = tokenResponse.getAdditionalParameters().get(OidcParameterNames.ID_TOKEN);

		// Most refresh responses won't carry one (id_token reissuance on refresh is AS-specific,
		// not spec-mandated) - nothing to sync, bail quietly.
		if (!(rawIdToken instanceof String idTokenValue) || idTokenValue.isBlank()) {
			return Mono.empty();
		}

		// This sync only makes sense for oauth2Login-based OIDC principals; if the current
		// Authentication isn't the expected shape, log why we're skipping rather than fail silently.
		if (!(context.getPrincipal() instanceof OAuth2AuthenticationToken currentAuth)
				|| !(currentAuth.getPrincipal() instanceof OidcUser currentOidcUser)) {
			log.warning("Refresh returned a new id_token but principal is not an OidcUser - skipping sync");
			return Mono.empty();
		}

		return this.idTokenDecoderFactory.createDecoder(clientRegistration)
				// Validate and parse the raw JWT string - same trust chain (issuer, audience,
				// signature against the AS's published JWKS) used when decoding ID Tokens at login.
				.decode(idTokenValue)

				// Convert the generic Jwt into the OidcIdToken value type Spring's OIDC model expects,
				// carrying over the token value and all decoded claims verbatim.
				.map(jwt -> new OidcIdToken(jwt.getTokenValue(), jwt.getIssuedAt(), jwt.getExpiresAt(), jwt.getClaims()))

				.flatMap(newIdToken -> {
					// UserInfo (if any) is unaffected by a token refresh - carry the existing one forward.
					OidcUserInfo userInfo = currentOidcUser.getUserInfo();

					// Build a fresh OidcUserAuthority from the NEW token (+ userInfo) - this is the
					// single source of truth every other copy below gets derived from.
					OidcUserAuthority refreshedOidcAuthority = (userInfo != null) ? new OidcUserAuthority(newIdToken, userInfo)
							: new OidcUserAuthority(newIdToken);

					// Rebuild the authorities collection: swap out only the OidcUserAuthority entry
					// (which is what embeds its own private copy of the id token) for the fresh one;
					// any other granted authority (SCOPE_openid, custom roles, etc.) passes through untouched.
					Set<GrantedAuthority> refreshedAuthorities = new LinkedHashSet<>();
					for (GrantedAuthority authority : currentOidcUser.getAuthorities()) {
						refreshedAuthorities.add(authority instanceof OidcUserAuthority ? refreshedOidcAuthority : authority);
					}

					// Rebuild the OidcUser: DefaultOidcUser stores its `authorities` and `idToken`
					// constructor args as two independent fields with no cross-checking, so both
					// must be the freshened versions or one will silently stay stale.
					OidcUser refreshedUser = (userInfo != null)
							? new DefaultOidcUser(refreshedAuthorities, newIdToken, userInfo)
							: new DefaultOidcUser(refreshedAuthorities, newIdToken);

					// Rebuild the OAuth2AuthenticationToken, reusing refreshedUser's OWN authorities
					// (not currentAuth.getAuthorities()) - OAuth2AuthenticationToken keeps its own
					// separate authorities list distinct from the principal's, and it's just as
					// prone to going stale if not rebuilt from the same source as everything else.
					OAuth2AuthenticationToken refreshedAuth = new OAuth2AuthenticationToken(
							refreshedUser, refreshedUser.getAuthorities(), currentAuth.getAuthorizedClientRegistrationId());

					// Record the sync for observability - sub claim only, never log token values.
					log.info("Synced refreshed id_token into SecurityContext (sub=" + newIdToken.getClaim(IdTokenClaimNames.SUB) + ")");

					// Persist the rebuilt SecurityContext through the supplied repository. For a
					// WebSessionServerSecurityContextRepository this writes to the same
					// "SPRING_SECURITY_CONTEXT" WebSession attribute the rest of the app already
					// reads from - Gateway's [SaveSession] filter flushes it to Redis afterward.
					return this.securityContextRepository.save(exchange, new SecurityContextImpl(refreshedAuth));
				});

	}

	/**
	 * @param token the token whose expiry is being checked (the access token, per the
	 *              stock provider's contract)
	 * @return {@code true} if {@code token.getExpiresAt() - clockSkew} is before "now",
	 * i.e. the token should be treated as expired and eligible for refresh
	 */
	private boolean hasTokenExpired(OAuth2Token token) {
		// Subtract the configured skew before comparing, so a token expiring a few seconds
		// from "now" is refreshed proactively rather than used right up to the wire.
		return this.clock.instant().isAfter(token.getExpiresAt().minus(this.clockSkew));
	}

	/**
	 * @param clockSkew maximum acceptable clock skew applied to the access-token expiry
	 *                  check in {@link #hasTokenExpired}; must be non-negative
	 */
	public void setClockSkew(Duration clockSkew) {
		Assert.notNull(clockSkew, "clockSkew cannot be null");
		Assert.isTrue(clockSkew.getSeconds() >= 0, "clockSkew must be >= 0");
		this.clockSkew = clockSkew;
	}
}
