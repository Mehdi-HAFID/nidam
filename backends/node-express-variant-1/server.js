import express from "express";
import {createRemoteJWKSet, jwtVerify} from "jose";

const app = express();
const PORT = 4003;
const ISSUER = "http://localhost:7080/auth";
const AUDIENCE = "client"; /* * Nidam publishes the public keys used to sign JWTs through * its JWKS endpoint. *
* The exact endpoint is published by the authorization
* server's OpenID Connect metadata. */
const JWKS = createRemoteJWKSet(new URL(`${ISSUER}/oauth2/jwks`));

async function authenticate(req, res, next) {
	const authorization = req.headers.authorization;
	if (!authorization || !authorization.startsWith("Bearer ")) {
		return res.status(401).json({error: "Unauthorized"});
	}
	const token = authorization.substring("Bearer ".length);
	try {
		const {payload} = await jwtVerify(token, JWKS, {issuer: ISSUER, audience: AUDIENCE});
		req.authentication = {
			token,
			claims: payload,
			authorities: Array.isArray(payload.authorities)
				? payload.authorities
				: [],
			name: payload.sub
		};
		next();
	} catch (error) {
		return res.status(401).json({error: "Invalid access token"});
	}
}

function requireAuthority(...requiredAuthorities) {
	return (req, res, next) => {
		const authorities = Array.isArray(req.authentication?.authorities)
			? req.authentication.authorities
			: [];

		const allowed = requiredAuthorities.some(
			authority => authorities.includes(authority)
		);

		if (!allowed) {
			return res.status(403).json({
				status: 403,
				code: "ACCESS_DENIED",
				message: "You do not have sufficient permissions"
			});
		}

		next();
	};
}

app.get("/demo", authenticate, requireAuthority("manage-users", "manage-projects"), (req, res) => {
	res.json(req.authentication);
});

app.get(
	"/top-secret",
	authenticate,
	requireAuthority("top-secret"),
	(req, res) => {
		res.json({
			message: "Top secret information"
		});
	}
);

app.get("/me", async (req, res) => {
	const authorization = req.headers.authorization;
	if (!authorization || !authorization.startsWith("Bearer ")) {
		return res.json({username: "", email: "", authorities: [], exp: Number.MAX_SAFE_INTEGER});
	}
	const token = authorization.substring("Bearer ".length);
	try {
		const {payload} = await jwtVerify(token, JWKS, {issuer: ISSUER, audience: AUDIENCE});
		res.json({
			username: payload.sub ?? "",
			email: payload.email ?? "",
			authorities: Array.isArray(payload.authorities) ? payload.authorities : [],
			exp: payload.exp ?? Number.MAX_SAFE_INTEGER
		});
	} catch (error) {
		return res.status(401).json({error: "Invalid access token"});
	}
});

app.listen(PORT, () => {
	console.log(`Backend listening on http://localhost:${PORT}`);
});