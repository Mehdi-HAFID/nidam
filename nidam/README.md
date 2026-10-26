# Nidam Service — Reference OAuth 2.0 Resource Server

The **Nidam** microservice is the reference OAuth 2.0 / OIDC Resource Server for the **Nidam** platform. Built with **Spring Boot** and **Spring Security (OAuth 2.0 Resource Server)**, it represents a secure downstream business backend that validates incoming JSON Web Tokens (JWTs) issued by `token-generator` and enforces role- and authority-based access control policies on protected resources.

---

## 1. Role in the Nidam Ecosystem

In the Nidam architecture, `nidam` acts as the protected business resource layer:
- **Stateless Token Verification**: Validates the cryptographic signatures of incoming JWT Bearer tokens against the public JWKS exposed by `token-generator` (`/auth/oauth2/jwks`).
- **Audience & Expiration Validation**: Enforces token validity, audience verification, and expiration constraints using `AudienceValidator`.
- **Authority-Based Access Control**: Maps token claims (such as `authorities` and roles) to Spring Security `GrantedAuthority` objects, guarding endpoints using method-level `@PreAuthorize` rules.
- **Relayed Consumption**: Receives requests forwarded by the `bff` via `/bff/api/**`, extracting user identity and permissions without direct coupling to browser session storage.

```
       [ Public Client / Browser ]
                    │
                    ▼ (Session Cookie)
          [ BFF Gateway (:7081) ]
                    │
                    ▼ (Authorization: Bearer <JWT Token>)
┌────────────────────────────────────────────────────────┐
│             Nidam Resource Server (:4003)              │
│  - Spring Security OAuth2 Resource Server              │
│  - Stateless RS256 JWT Verification via JWKS           │
│  - AudienceValidator & Token Introspection             │
│  - Method-Level Security (@PreAuthorize)               │
└────────────────────────────────────────────────────────┘
                    │
                    ▼ (Public JWKS Fetch on startup/rotation)
       [ Token Generator (:4002) ]
```

---

## 2. Core Features & Capabilities

- **Decentralized Cryptographic Validation**: Validates JWTs statelessly without making per-request calls to the authorization server, maximizing throughput and fault tolerance.
- **Authority Extraction & RBAC**: Transparently parses custom claims (e.g. `manage-users`, `manage-projects`, `ROLE_ADMIN`) into application-level security context.
- **Detailed User Profile Inspection (`/me`)**: Exposes authenticated principal attributes (username, email, authorities, expiration timestamp) consumed by frontend dashboards.
- **Method-Level Security Demos**: Provides reference implementations of fine-grained access control (`/demo`, `/top-secret`) to verify permission evaluation.

---

## 3. Key Endpoints

| HTTP Method | Endpoint | Required Authority / Scope | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/me` | Public and Authenticated JWT | Returns the current user's profile, email, authorities, and token expiration |
| `GET` | `/demo` | `manage-users` OR `manage-projects` | Demonstrates access to a protected resource; returns token metadata |
| `GET` | `/top-secret` | `top-secret` | Demonstrates restricted access; returns sensitive content |
| `GET` | `/actuator/health` | Public | Service health check |

### Example Response from `/me`
```json
{
  "email": "jane@example.com",
  "authorities": [
    "manage-users",
    "manage-projects"
  ]
}
```

---

## 4. Configuration Reference

Key properties in `src/main/resources/application.yml` or overridden via `configuration.yml`:

```yaml
server:
  port: 4003

profile-public-endpoint: /me

spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://localhost:7080/auth/oauth2/jwks
          issuer-uri: http://localhost:7080/auth
```

---

## 5. Building and Running

### Prerequisites
- Java 25+ JDK
- Maven 3.10+ (or included `./mvnw`)
- Running `token-generator` (port 4002 / 7080)

### Local Development
```bash
# Run with Spring Boot Maven plugin (dev profile)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Or if you have PowerShell 7+ installed, run from the dev/ folder:
.\nidam-dev.ps1 start

# Or package and run the executable JAR in production mode
mvn clean package -DskipTests
java -jar target/nidam-*.jar --spring.profiles.active=prod
```

---

## 6. Part of the Nidam Platform
- **[token-generator](../token-generator/README.md)**: OAuth 2.0 Authorization Server that issues the JWTs validated by this service.
- **[bff](../bff/README.md)**: Backend-For-Frontend gateway that relays requests and injects the `Bearer` token.
- **[reverse-proxy](../reverse-proxy/README.md)**: Public edge proxy routing traffic into the ecosystem.
- **[nidam-spa](../nidam-spa/README.md)**: React frontend consuming endpoints on this resource server.
- **[registration](../registration/README.md)**: User registration service.
- **[spa-server](../spa-server/README.md)**: Static host for the React frontend.
- **[backend/](../backend/)**: Alternative resource server implementations in ASP.NET Core, Go, Micronaut, Jakarta/OpenLiberty, Node.js/Express, and FastAPI.
