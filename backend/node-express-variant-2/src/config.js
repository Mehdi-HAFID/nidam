/**
 * Centralized configuration, mirroring the shape of Nidam's `application.yml`.
 *
 * Every value can be overridden via environment variables so this server
 * can be pointed at any Nidam Authorization Server instance without
 * touching code — same intent as Nidam's `${...}` placeholder resolution.
 */
const host = process.env.HOST || 'http://localhost';
const reverseProxyPort = process.env.REVERSE_PROXY_PORT || '7080';
const reverseProxyUri = `${host}:${reverseProxyPort}`;

const authorizationServerPrefix = process.env.AUTHORIZATION_SERVER_PREFIX || '/auth';

export default {
	/** Expected issuer URL from the Authorization Server (must match `iss` claim exactly). */
	issuer: process.env.ISSUER || `${reverseProxyUri}${authorizationServerPrefix}`,

	/** Expected audience value in the JWT token. Defaults to "client", same as Nidam. */
	expectedAudience: process.env.CLIENT_ID || 'client',

	/** Port this resource server listens on. Pick something free — Nidam's own is 4003. */
	port: process.env.RESOURCE_SERVER_PORT || 4003,

	/** Path that must remain accessible to anonymous callers. */
	publicProfileEndpoint: process.env.PROFILE_PUBLIC_ENDPOINT || '/me',

	/** Tolerance (seconds) applied to exp/nbf checks — matches Nidam's JwtTimestampValidator. */
	clockToleranceSeconds: 60,
};
