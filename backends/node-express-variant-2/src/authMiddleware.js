import {verifyAccessToken} from "./jwtVerifier.js";

/**
 * Extracts and verifies the Bearer token from the Authorization header,
 * attaching the decoded claims to `req.auth` on success.
 *
 * Behavior intentionally matches Spring's BearerTokenAuthenticationFilter:
 *  - No Authorization header at all → treated as anonymous, request
 *    passes through untouched (public endpoints decide what to do).
 *  - Header present but invalid/expired/wrong audience → 401 immediately,
 *    equivalent to Nidam's `authenticationEntryPoint`. This happens even
 *    for endpoints that would otherwise be public, because authentication
 *    failure is evaluated before authorization in Spring's chain too.
 */
async function authenticate(req, res, next) {
	const header = req.headers.authorization;
	if (!header) {
		return next();
	}
	const [scheme, token] = header.split(' ');
	if (scheme !== 'Bearer' || !token) {
		return res.status(401).end();
	}
	try {
		req.auth = await verifyAccessToken(token);
		next();
	} catch (err) {
		res.status(401).end();
	}
}

/**
 * Route guard requiring an authenticated caller.
 * Equivalent to `.anyRequest().authenticated()`.
 */
function requireAuth(req, res, next) {
	if (!req.auth) {
		return res.status(401).end();
	}
	next();
}

/**
 * Route guard equivalent to Spring's `@PreAuthorize("hasAuthority(...)")`.
 *
 * Reads the custom "authorities" claim from the token — the same claim
 * name Nidam's Authorization Server issues — and grants access if the
 * caller holds at least one of the required authorities.
 *
 * @param {...string} required - authorities, any one of which is sufficient
 */
function hasAnyAuthority(...required) {
	return (req, res, next) => {
		if (!req.auth) {
			return res.status(401).end();
		}
		const authorities = Array.isArray(req.auth.authorities) ? req.auth.authorities : [];
		const granted = required.some((a) => authorities.includes(a));
		if (!granted) {
			return res.status(403).json({
				status: 403,
				code: "ACCESS_DENIED",
				message: "You do not have sufficient permissions"
			});
		}
		next();
	};
}

export {authenticate, requireAuth, hasAnyAuthority};
