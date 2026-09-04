import express from "express";

import config from "./config.js";
import {authenticate, hasAnyAuthority, requireAuth} from "./authMiddleware.js";


const app = express();

// Attempts to verify a Bearer token on every request, if one is present.
app.use(authenticate);

/**
 * Public profile endpoint — equivalent to Nidam's `MeController`.
 *
 * Returns the caller's identity when a valid token was presented, or an
 * anonymous payload otherwise. Never itself requires authentication,
 * mirroring `profile-public-endpoint: /me` being `permitAll()` in Nidam's
 * `SecurityConfig`.
 */
app.get(config.publicProfileEndpoint, (req, res) => {
	if (!req.auth) {
		return res.json({username: '', email: '', authorities: [], exp: Number.MAX_SAFE_INTEGER});
	}
	const {sub, email, authorities, exp} = req.auth;
	res.json({
		username: sub,
		email: email || '',
		authorities: Array.isArray(authorities) ? authorities : [],
		exp,
	});
});

/**
 * Equivalent to `DemoController#allowedResource`.
 * Accessible with either `manage-users` or `manage-projects` — the two
 * authorities Nidam grants by default at sign-up.
 */
app.get('/demo', requireAuth, hasAnyAuthority('manage-users', 'manage-projects'), (req, res) => {
	res.json(req.auth);
});

/**
 * Equivalent to `DemoController#forbiddenResource`.
 * No default user holds `top-secret`, so out of the box this always 403s —
 * the same demonstration Nidam's Spring version gives.
 */
app.get('/top-secret', requireAuth, hasAnyAuthority('top-secret'), (req, res) => {
	res.send('Top secret information');
});

app.listen(config.port, () => {
	console.log(`Node resource server listening on ${config.port}, trusting issuer ${config.issuer}`);
});
