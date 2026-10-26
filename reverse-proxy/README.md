# Reverse Proxy Service — Nidam Unified Edge Gateway

The **Reverse Proxy** is the primary edge gateway and reverse proxy for the **Nidam** platform. Built on **Spring Cloud Gateway (WebFlux)**, it exposes a single unified HTTP/HTTPS entry point (port `7080`), routes incoming traffic to internal microservices based on URL path prefixes, injects essential security headers, and provides client-side load balancing and health checks for high-availability deployments.

---

## 1. Role in the Nidam Ecosystem

The reverse proxy serves as the public front door to all Nidam capabilities, solving several key architectural needs:
- **Single-Origin Policy**: By exposing the frontend SPA, BFF, Authorization Server, and Registration API under one host and port (`localhost:7080`), it avoids Cross-Origin Resource Sharing (CORS) complications and enables secure SameSite cookie handling.
- **Path-Based Routing**: Dispatches requests based on path prefixes to upstream microservices.
- **Cluster Load Balancing**: Automatically distributes requests across multiple instances of upstream services using Spring Cloud LoadBalancer (`lb://`) with background health-check probes.
- **Edge Security Hardening**: Injects HTTP security headers (`X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`) and normalizes `X-Forwarded-*` headers.

```
                                  [ Public Clients / Web Browser ]
                                                 │
                                                 ▼ (HTTP/HTTPS :7080)
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                 Reverse Proxy (:7080)                                  │
│  - Spring Cloud Gateway (WebFlux)                                                      │
│  - Edge Security Headers (Frame-Options, Content-Type-Options)                         │
│  - Client-Side Load Balancing (lb:// with periodic health checks)                     │
│  - Transparent Header Propagation (X-Forwarded-Proto, Host Preservation)               │
└────────────────────────────────────────────────────────────────────────────────────────┘
         │                          │                       │                      │
         ▼ (/spa/**)                ▼ (/auth/**)            ▼ (/bff/**)            ▼ (/registration/**)
┌─────────────────┐       ┌──────────────────┐    ┌─────────────────┐    ┌──────────────────────┐
│  SPA Server     │       │ Token Generator  │    │  BFF Gateway    │    │ Registration Service │
│     (:4001)     │       │     (:4002)      │    │     (:7081)     │    │       (:4000)        │
└─────────────────┘       └──────────────────┘    └─────────────────┘    └──────────────────────┘
```

---

## 2. Core Features & Capabilities

- **Unified Routing Matrix**:
  - `/spa/**` $\rightarrow$ Routes to static SPA server or React development server (`:4001`).
  - `/auth/**` $\rightarrow$ Routes to `token-generator` Authorization Server (`:4002`).
  - `/bff/**` $\rightarrow$ Strips prefix and routes to `bff` (`:7081`).
  - `/registration/**` $\rightarrow$ Strips prefix and routes to `registration` (`:4000`).
- **Load Balancing & Dynamic Health Probing**: Periodically probes `/actuator/health` across configured instances to guarantee traffic is only directed to healthy nodes.
- **Host & Protocol Preservation**: Employs `PreserveHostHeader` and explicitly populates `X-Forwarded-Proto` and `X-Forwarded-Port` so backend services generate accurate redirect URIs.
- **Default Security Headers**: Automatically applies protective security headers across all responses.

---

## 3. Routing Table

| Inbound Path | Target Service | Default Internal Port | Transform Rules |
| :--- | :--- | :--- | :--- |
| `http://localhost:7080/spa/**` | `nidam-spa` / `spa-server` | `4001` | Preserve host, forward proto |
| `http://localhost:7080/auth/**` | `token-generator` | `4002` | Preserve host, forward port/proto |
| `http://localhost:7080/bff/**` | `bff` | `7081` | Strip prefix (`1`), forward proto |
| `http://localhost:7080/registration/**` | `registration` | `4000` | Strip prefix (`1`), forward proto |

---

## 4. Configuration Reference

Key properties in `src/main/resources/application.yml` or overridden via `configuration.yml`:

```yaml
server:
  port: 7080

nidam:
  protocol: http    # 'http' or 'https'

spring:
  cloud:
    gateway:
      server:
        webflux:
          default-filters:
            - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin
            - AddResponseHeader=X-Frame-Options, DENY
            - AddResponseHeader=X-Content-Type-Options, nosniff
            - AddResponseHeader=Referrer-Policy, no-referrer
          routes:
            - id: spa
              uri: http://localhost:4001/spa
              predicates:
                - Path=/spa/**
              filters:
                - PreserveHostHeader
                - SetRequestHeader=X-Forwarded-Proto, ${nidam.protocol}
            - id: authorization-server
              uri: lb://token-generator
              predicates:
                - Path=/auth/**
              filters:
                - PreserveHostHeader
                - SetRequestHeader=X-Forwarded-Proto, ${nidam.protocol}
                - SetRequestHeader=X-Forwarded-Port, 7080
            - id: bff
              uri: lb://bff
              predicates:
                - Path=/bff/**
              filters:
                - StripPrefix=1
                - PreserveHostHeader
            - id: registration
              uri: lb://registration
              predicates:
                - Path=/registration/**
              filters:
                - StripPrefix=1
                - PreserveHostHeader
```

---

## 5. Building and Running

### Prerequisites
- Java 25+ JDK
- Maven 3.10+ (or included `./mvnw`)

### Local Development
```bash
# Run with Spring Boot Maven plugin (dev profile)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Or if you have PowerShell 7+ installed, run from the dev/ folder:
.\nidam-dev.ps1 start

# Or package and run the executable JAR in production mode
mvn clean package -DskipTests
java -jar target/reverse-proxy-*.jar --spring.profiles.active=prod
```

---

## 6. Part of the Nidam Platform
- **[token-generator](../token-generator/README.md)**: OAuth 2.0 Authorization Server routed via `/auth/**`.
- **[bff](../bff/README.md)**: Session and token handler gateway routed via `/bff/**`.
- **[registration](../registration/README.md)**: User provisioning service routed via `/registration/**`.
- **[nidam-spa](../nidam-spa/README.md)**: React frontend application routed via `/spa/**`.
- **[spa-server](../spa-server/README.md)**: Static host and config server for the SPA.
- **[nidam](../nidam/README.md)**: Protected resource server.
