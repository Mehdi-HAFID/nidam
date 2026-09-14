import {createRemoteJWKSet, jwtVerify} from "jose";
import config from "./config.js";

let jwks;
let discoveryPromise;

/**
 * Performs OpenID Connect discovery against the issuer's
 * `/.well-known/openid-configuration` document and lazily builds a
 * remote JWK Set from the published `jwks_uri`.
 *
 * Equivalent to Spring's `JwtDecoders.fromIssuerLocation(issuer)`, which
 * does this same discovery-then-JWKS-fetch dance under the hood.
 *
 * The returned JWK Set is cached for the life of the process; `jose`
 * re-fetches automatically whenever it encounters a `kid` it doesn't
 * recognize, so key rotation on the Authorization Server side is handled
 * without a restart.
 *
 * @returns {Promise<import('jose').JWTVerifyGetKey>}
 */
async function getJwks() {
	if (jwks) return jwks;
	if (!discoveryPromise) {
		discoveryPromise = fetch(`${config.issuer}/.well-known/openid-configuration`)
			.then((res) => {
				if (!res.ok) {
					throw new Error(`OIDC discovery failed: ${res.status} ${res.statusText}`);
				}
				return res.json();
			})
			.then((doc) => {
				jwks = createRemoteJWKSet(new URL(doc.jwks_uri));
				return jwks;
			})
			.catch((err) => {
				// Don't cache a failed discovery attempt — let the next call retry.
				discoveryPromise = undefined;
				throw err;
			});
	}
	return discoveryPromise;
}

/**
 * Verifies a raw JWT access token against Nidam's Authorization Server.
 *
 * Mirrors the validation chain Nidam wires into its `JwtDecoder`:
 *  - signature checked against the issuer's published JWK Set
 *  - `iss` claim must equal the configured issuer exactly
 *  - `aud` claim must contain the expected audience (like `AudienceValidator`)
 *  - `exp` / `nbf` checked with a 60s clock skew tolerance
 *
 * @param {string} token - the raw bearer token, without the "Bearer " prefix
 * @returns {Promise<import('jose').JWTPayload>} the verified claims
 * @throws if the token is missing, malformed, expired, or fails any check above
 */
async function verifyAccessToken(token) {
	const keySet = await getJwks();
	const {payload} = await jwtVerify(token, keySet, {
		issuer: config.issuer,
		audience: config.expectedAudience,
		clockTolerance: config.clockToleranceSeconds,
	});
	return payload;
}

export {verifyAccessToken};
