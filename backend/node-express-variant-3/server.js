import express from "express";
import jwt from "jsonwebtoken";
import jwksClient from "jwks-rsa";

const app = express();
const PORT = 4003;
const ISSUER = 'http://localhost:7080/auth';
const AUDIENCE = 'client';

// 1. Configure the JWKS client to fetch public keys from Nidam Auth Server
const client = jwksClient({
	jwksUri: `${ISSUER}/oauth2/jwks` // Standard Spring Authorization Server JWKS endpoint
});

function getKey(header, callback) {
	client.getSigningKey(header.kid, function (err, key) {
		const signingKey = key?.publicKey || key?.rsaPublicKey;
		callback(null, signingKey);
	});
}

// 2. Middleware to extract and validate the JWT
const authenticateToken = (req, res, next) => {
	const authHeader = req.headers['authorization'];
	const token = authHeader && authHeader.split(' ')[1];

	if (!token) {
		req.user = null; // Proceed as anonymous
		return next();
	}

	jwt.verify(token, getKey, {
		issuer: ISSUER,
		audience: AUDIENCE,
		clockTolerance: 60 // 60 seconds clock skew tolerance
	}, (err, decoded) => {
		if (err) {
			return res.status(401).json({error: 'Unauthorized', message: err.message});
		}
		req.token = token
		req.user = decoded; // Contains standard claims + 'authorities' array
		next();
	});
};

// 3. Security Middlewares
const requireAuth = (req, res, next) => {
	if (!req.user) return res.status(401).json({error: 'Authentication required'});
	next();
};

const hasAnyAuthority = (...allowedAuthorities) => {
	return (req, res, next) => {
		const userAuthorities = req.user?.authorities || [];
		const hasMatch = allowedAuthorities.some(auth => userAuthorities.includes(auth));

		if (hasMatch) {
			next();
		} else {
			res.status(403).json({
				status: 403,
				code: "ACCESS_DENIED",
				message: "You do not have sufficient permissions"
			});
		}
	};
};

// Apply JWT parsing globally
app.use(authenticateToken);

// --- Endpoints (Mirroring DemoController & MeController) ---

// Public endpoint: returns UserInfoDto or ANONYMOUS equivalent
app.get('/me', (req, res) => {
	if (req.user) {
		res.json({
			username: req.user.sub || '',
			email: req.user.email || '',
			authorities: req.user.authorities || [],
			exp: req.user.exp || null
		});
	} else {
		res.json({
			username: '',
			email: '',
			authorities: [],
			exp: Number.MAX_SAFE_INTEGER // Java's Long.MAX_VALUE equivalent
		});
	}
});

// Protected: Requires either manage-users OR manage-projects
app.get('/demo', requireAuth, hasAnyAuthority('manage-users', 'manage-projects'), (req, res) => {
	res.json({
		tokenValue: req.token,
		claims: req.user,
		authorities: req.user.authorities || []
	}); // Echos back the decoded JWT claims
});

// Protected: Requires top-secret
app.get('/top-secret', requireAuth, hasAnyAuthority('top-secret'), (req, res) => {
	res.send('Top secret information');
});

app.listen(PORT, () => {
	console.log(`Node.js Resource Server listening on port ${PORT}`);
});