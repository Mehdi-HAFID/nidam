# Token Generator Service — Nidam Authorization Server

The **Token Generator** is the central Identity Provider (IdP) and OAuth 2.0 / OpenID Connect (OIDC) Authorization Server for the **Nidam** platform. Built on **Spring Authorization Server** and **Spring Security**, it manages user authentication, issues cryptographically signed JSON Web Tokens (JWTs), enforces authentication rate limiting, and coordinates secure single sign-on (SSO) and RP-initiated logout across the ecosystem.

---

## 1. Role in the Nidam Ecosystem

In the Nidam architecture, `token-generator` acts as the trusted authority for issuing access tokens and ID tokens:
- **Directs Login Flows**: Provides the interactive login interface and processes user credential validation.
- **Signs and Issues Tokens**: Creates RS256-signed JWT access and ID tokens containing custom claims (e.g., fine-grained user authorities) consumed by downstream resource servers (`nidam`, `backend/*`) and the `bff`.
- **Exposes JWKS**: Publishes public keys via `/auth/oauth2/jwks` enabling decentralized, stateless cryptographic token verification across all resource servers.
- **Coordinates Session Termination**: Handles OIDC RP-initiated logout requests from the BFF, invalidating active sessions.

```
       [ Browser ]
            │  ▲
 1. Login   │  │ 2. Redirect to Auth Server
 (via Proxy)│  │
            ▼  │
   [ Reverse Proxy (:7080) ]
            │
            ▼ (Path: /auth/**)
┌────────────────────────────────────────────────────────┐
│             Token Generator (:4002)                    │
│  - Spring Authorization Server (OIDC / OAuth 2.0)      │
│  - Custom JWT Claims Customizer (Authorities, Email)   │
│  - Password Encoders (SCrypt, BCrypt, Argon2, PBKDF2)  │
│  - Rate Limiter & Brute-Force Protection               │
│  - High-Availability Sessions (Redis / Tomcat)         │
└────────────────────────────────────────────────────────┘
            │                        │
            ▼ (SQL / Mongo)          ▼ (Optional Cluster Cache)
   [(H2 / MySQL / Mongo)]         [ Redis (:6379) ]
```

---

## 2. Core Features & Capabilities

- **OAuth 2.0 & OpenID Connect 1.0 Provider**: Implements standard Authorization Code Grant with PKCE, Refresh Token Grant, and OIDC discovery metadata (`/.well-known/openid-configuration`).
- **Flexible Token Claim Customization**: Embeds application-level authorities (`manage-users`, `manage-projects`, etc.) and user attributes directly into the access token payload for stateless downstream authorization.
- **Pluggable & Hybrid Password Encoders**: Supports modern hashing schemes including SCrypt (default), BCrypt, Argon2, and PBKDF2, as well as idless legacy hashes.
- **Dual Persistence Architecture**:
  - **SQL**: JPA/Hibernate support for MySQL and embedded/standalone H2 and others databases.
  - **NoSQL**: Native MongoDB repository integration.
- **Rate Limiting & Brute-Force Defense**: Built-in sliding window rate limiter locking out offending IPs/accounts after configurable failed attempts.
- **Clustering & High Availability**: Supports distributed session storage and distributed OAuth2 authorization state via Redis (`nidam:token-generator` namespace).
- **RP-Initiated Logout**: Custom logout response handler (`RedisOIDCLogoutResponseHandler`) managing `id_token_hint` validation and post-logout redirection back to the frontend.

---

## 3. Key Endpoints

| HTTP Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/auth/login` | Renders interactive login page |
| `POST` | `/auth/login` | Processes username and password authentication |
| `GET` / `POST` | `/auth/oauth2/authorize` | OAuth 2.0 authorization request endpoint |
| `POST` | `/auth/oauth2/token` | Issues access tokens, refresh tokens, and ID tokens |
| `GET` | `/auth/oauth2/jwks` | JSON Web Key Set containing public RSA verification keys |
| `GET` / `POST` | `/auth/connect/logout` | OIDC RP-initiated logout endpoint |
| `GET` | `/auth/actuator/health` | Service health status |

---

## 4. Configuration Reference

Key application properties configured in `src/main/resources/application.yml` or overridden via `configuration.yml`:

```yaml
authorization-server-port: 4002
authorization-server-prefix: /auth
issuer: http://localhost:7080/auth

# Persistence: 'sql' or 'mongo'
nidam:
  persistence-mode: "sql"
  session-mode: "tomcat"    # 'tomcat' (standalone) or 'redis' (clustered)

# Password Hashing Hierarchy
password-encoders: scrypt,bcrypt,argon2,pbkdf2
password-idless: scrypt

# Brute-force Rate Limiting
rate-limiter:
  enabled: true
  lockout-duration: 5       # minutes
  max-attempts: 5
  cleanup-interval-ms: 60000

# Token Lifespans
ttl:
  access-token: 1h
  id-token: 1h
  refresh-token: 7d
  auth-code: 5m
```

---

## 5. Building and Running

### Prerequisites
- Java 25+ JDK
- Maven 3.10+ (or included `./mvnw`)
- Running Database (H2 TCP server on port 9092, MySQL on 3306, or MongoDB on 27017)

### Local Development
```bash
# Run with Spring Boot Maven plugin (dev profile)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Or if you have PowerShell 7+ installed, run from the dev/ folder:
.\nidam-dev.ps1 start

# Or package and run the executable JAR in production mode
mvn clean package -DskipTests
java -jar target/token-generator-*.jar --spring.profiles.active=prod
```

---

## 6. Part of the Nidam Platform
- **[bff](../bff/README.md)**: OAuth 2.0 client & session gateway forwarding requests with injected Bearer tokens.
- **[reverse-proxy](../reverse-proxy/README.md)**: Unified entry gateway routing `/auth/**` traffic to this service.
- **[registration](../registration/README.md)**: User provisioning service populating the shared user database.
- **[nidam](../nidam/README.md)**: Reference Spring Boot Resource Server verifying tokens issued here.
- **[nidam-spa](../nidam-spa/README.md)**: React frontend user interface.
- **[spa-server](../spa-server/README.md)**: Dynamic configuration host for the frontend.
