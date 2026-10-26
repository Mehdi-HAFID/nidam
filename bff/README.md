# BFF Service — Backend For Frontend Gateway

The **BFF (Backend For Frontend)** is a specialized security gateway and OAuth 2.0 Client in the **Nidam** architecture. Built with **Spring Cloud Gateway** and **Spring Security (OAuth2 Client)**, it implements the **Token Handler Pattern** to completely isolate browser Single Page Applications (SPAs) from directly handling, storing, or managing sensitive OAuth 2.0 access and refresh tokens.

---

## 1. Role in the Nidam Ecosystem

The BFF eliminates token exposure vulnerabilities (XSS token theft) by maintaining encrypted, HTTP-only, SameSite session cookies with the browser, while acting as a confidential OAuth 2.0 client toward the Authorization Server (`token-generator`):

- **Confidential OAuth 2.0 Client**: Holds the `client_id` and `client_secret`, executing the Authorization Code Grant exchange server-side.
- **Secure Session Bridge**: Maps the browser's HTTP-only session cookie to active OAuth 2.0 access and refresh tokens stored in memory or Redis.
- **Token Relay Reverse Proxy**: Intercepts requests destined for backend resource servers (`/bff/api/**`), automatically retrieves the user's active access token, attaches it as `Authorization: Bearer <token>`, and forwards the request downstream.
- **Seamless Token Refresh**: Automatically uses the refresh token to renew expired access tokens before relaying requests, preventing user disruption.
- **Coordinated Single Logout**: Handles application logout, clears local session data, and triggers OIDC RP-initiated logout with `id_token_hint` against `token-generator`.

```
                    [ Browser / SPA ]
                            │  ▲
   HTTP-Only Session Cookie │  │ Encrypted Session Cookie
                            ▼  │
                [ Reverse Proxy (:7080) ]
                            │
                            ▼ (Path: /bff/**)
┌────────────────────────────────────────────────────────┐
│                      BFF (:7081)                       │
│  - Spring Security OAuth2 Client                       │
│  - Spring Cloud Gateway (TokenRelay Filter)            │
│  - Automated Token Refresh Rotation                    │
│  - Centralized Session State (Redis / Tomcat)          │
└────────────────────────────────────────────────────────┘
          │ (OAuth2 Code / Token)      │ (Bearer JWT Token)
          ▼                            ▼
[ Token Generator (:4002) ]    [ Resource Server / Nidam (:4003) ]
```

---

## 2. Core Features & Capabilities

- **Token Relay Pattern**: Employs Spring Cloud Gateway's `TokenRelay` filter to transparently attach valid OAuth 2.0 Bearer tokens to all downstream API requests (`/api/**`).
- **Session-Based Token Storage**: Avoids exposing access/refresh tokens to browser JavaScript (`localStorage` / `sessionStorage`), mitigating XSS-based credential extraction.
- **Automatic Refresh Token Rotation**: Implements `OidcRefreshSessionRotationSuccessHandler` to seamlessly refresh access tokens on-the-fly and update stored session state.
- **High-Availability & Distributed Sessions**: Supports Redis-backed distributed sessions (`nidam:bff` namespace) for multi-instance deployments.
- **CORS & Header Normalization**: Automatically dedupes CORS response headers (`Access-Control-Allow-Credentials`, `Access-Control-Allow-Origin`) and strips routing prefixes.
- **OIDC RP-Initiated Logout Coordination**: Reads the active ID token hint and constructs compliant logout URLs to terminate the user session at the IdP.

---

## 3. Key Endpoints & Routes

| Endpoint | Type | Description |
| :--- | :--- | :--- |
| `/bff/oauth2/authorization/token-generator` | Endpoint | Initiates OAuth 2.0 Authorization Code flow redirecting to Authorization Server |
| `/bff/login/oauth2/code/token-generator` | Endpoint | OAuth 2.0 redirect callback endpoint exchanging authorization code for tokens |
| `/bff/logout` | Endpoint | Initiates OIDC RP-initiated logout and destroys local session |
| `/bff/post-logout` | Endpoint | Post-logout redirection destination |
| `/bff/api/**` | Gateway Route | Proxies requests to downstream Resource Server with injected `Bearer` token |
| `/actuator/health` | Management | Health check and readiness probe |

---

## 4. Configuration Reference

Key properties in `src/main/resources/application.yml` or overridden via `configuration.yml`:

```yaml
bff-port: 7081
bff-prefix: /bff
issuer: http://localhost:7080/auth
resource-server-uri: http://localhost:4003

client:
  id: client
  secret: secret

# Proxy route to backend Resource Server
bff-resource-server-route-prefix: /api
bff-resource-server-route-predicate: /api/**

# Session Management
nidam:
  session-mode: "tomcat"    # 'tomcat' (standalone) or 'redis' (clustered)
redis-url: redis://:mehdi@localhost:6379
redis-bff-namespace: nidam:bff

# Spring Cloud Gateway Route
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: bff
              uri: ${resource-server-uri}
              predicates:
                - Path=${bff-resource-server-route-predicate}
              filters:
                - TokenRelay=
                - StripPrefix=1
```

---

## 5. Building and Running

### Prerequisites
- Java 25+ JDK
- Maven 3.10+ (or included `./mvnw`)
- Running `token-generator` (:4002) and `nidam` (:4003)

### Local Development
```bash
# Run with Spring Boot Maven plugin (dev profile)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Or if you have PowerShell 7+ installed, run from the dev/ folder:
.\nidam-dev.ps1 start

# Or package and run the executable JAR in production mode
mvn clean package -DskipTests
java -jar target/bff-*.jar --spring.profiles.active=prod
```

---

## 6. Part of the Nidam Platform
- **[token-generator](../token-generator/README.md)**: OAuth 2.0 Authorization Server that authenticates users and issues tokens.
- **[reverse-proxy](../reverse-proxy/README.md)**: Public edge proxy routing `/bff/**` to this gateway.
- **[nidam](../nidam/README.md)**: Protected Resource Server receiving Bearer tokens relayed by the BFF.
- **[nidam-spa](../nidam-spa/README.md)**: Frontend application communicating with the BFF using HTTP-only session cookies.
- **[registration](../registration/README.md)**: User signup microservice.
- **[spa-server](../spa-server/README.md)**: Static host serving frontend code.
