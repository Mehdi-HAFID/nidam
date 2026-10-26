# Nidam Platform — Secure Full-Stack OAuth 2.0 & OIDC Identity Architecture

**Nidam** is a modern, enterprise-grade authentication and authorization platform designed to deliver secure, token-isolated Single Page Applications (SPAs) and microservice ecosystems. Built on **Spring Boot**, **Spring Authorization Server**, **Spring Cloud Gateway**, and **React**, Nidam implements industry-standard security architectures including the **Backend For Frontend (BFF)** pattern, **Token Handler Pattern**, and decentralized stateless JWT validation.

---

## 1. High-Level Architecture Overview

```
                                      [ Public Client / Web Browser ]
                                                     │
                                                     ▼ (HTTP / HTTPS :7080)
┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                                  Reverse Proxy (:7080)                                                 │
│  - Unified Single Origin & Edge Routing (Spring Cloud Gateway)                                                         │
│  - Load Balancing & Health Check Probing (lb://)                                                                       │
│  - Security Response Header Enforcement (X-Frame-Options, Content-Type-Options)                                         │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
          │ (Path: /spa/**)                │ (Path: /auth/**)                 │ (Path: /bff/**)          │ (Path: /registration/**)
          ▼                                ▼                                  ▼                          ▼
┌──────────────────┐             ┌─────────────────────┐            ┌────────────────────┐     ┌───────────────────────┐
│ SPA Host (:4001) │             │   Token Generator   │            │    BFF Gateway     │     │ Registration Service  │
│ - nidam-spa      │             │       (:4002)       │            │      (:7081)       │     │        (:4000)        │
│ - spa-server     │             │ - Auth Server       │            │ - OAuth2 Client    │     │ - User Provisioning   │
│ - Dynamic Config │             │ - OIDC Provider     │            │ - Token Relay      │     │ - Password Hashing    │
│                  │             │ - Custom JWT Claims │            │ - Cookie Sessions  │     │ - reCAPTCHA Verify    │
└──────────────────┘             └─────────────────────┘            └────────────────────┘     └───────────────────────┘
                                            │                                  │ (Bearer JWT)              │
                                            │                                  ▼                           │
                                            │                      ┌───────────────────────┐               │
                                            │                      │ Nidam Resource Server │               │
                                            │                      │        (:4003)        │               │
                                            │                      │ - Stateless JWT Valid │               │
                                            │                      │ - Method Security     │               │
                                            │                      └───────────────────────┘               │
                                            │ (User DB)                                                    │ (User DB)
                                            ▼                                                              ▼
                                ┌────────────────────────────────────────────────────────────────────────────────────────────┐
                                │ Identity Store (H2 / MySQL / PostgreSQL / MariaDB / SQL Server / Oracle / IBM DB2 / Mongo) │
                                └────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Platform Services & Repositories

| Service Directory | Role & Tech Stack | Default Port | Documentation |
| :--- | :--- | :--- | :--- |
| **[`reverse-proxy`](./reverse-proxy/README.md)** | Edge Gateway, Unified Origin, Load Balancer (Spring Cloud Gateway) | `7080` | [README](./reverse-proxy/README.md) |
| **[`token-generator`](./token-generator/README.md)** | OIDC / OAuth 2.0 Authorization Server & IdP (Spring Authorization Server) | `4002` (via `/auth`) | [README](./token-generator/README.md) |
| **[`bff`](./bff/README.md)** | Backend For Frontend Gateway & Token Handler (Spring Security OAuth2 Client) | `7081` (via `/bff`) | [README](./bff/README.md) |
| **[`registration`](./registration/README.md)** | User Signup, Bot Protection & Multi-Hash Provisioning (Spring Boot) | `4000` (via `/registration`) | [README](./registration/README.md) |
| **[`nidam`](./nidam/README.md)** | Reference Protected Resource Server (Spring Security Resource Server) | `4003` (via `/bff/api`) | [README](./nidam/README.md) |
| **[`nidam-spa`](./nidam-spa/README.md)** | Responsive Web Frontend (React 18, Redux Toolkit, Redux-Saga) | `4001` (via `/spa`) | [README](./nidam-spa/README.md) |
| **[`spa-server`](./spa-server/README.md)** | Static SPA Host & Dynamic Runtime Config Provider (Spring WebFlux) | `4001` (via `/spa`) | [README](./spa-server/README.md) |
| **[`backend/`](./backend/)** | Multi-Language Resource Server Reference Implementations (.NET, Go, Python, Node, Micronaut, Liberty) | Variable | Reference Implementations |
| **[`frontend/nextjs`](./frontend/nextjs)** | Next.js Frontend Alternative Implementation | `3000` | Frontend Variant |

---

## 3. Core Architectural Highlights

1. **Token Handler Pattern (BFF)**: Browser clients never access, hold, or persist OAuth access or refresh tokens. Instead, secure, HTTP-only, encrypted session cookies are exchanged between the browser and the BFF. The BFF relays authorized calls to downstream resource servers attaching cryptographically signed `Bearer <JWT>` tokens.
2. **Single-Origin Deployment**: All public communications flow through the `reverse-proxy` on port `7080`, removing browser CORS restrictions and simplifying cookie security policies.
3. **Decentralized Verification**: Resource servers validate JWT signatures statelessly using public keys published at `/auth/oauth2/jwks` without querying the database or authorization server on every request.
4. **Environment Portability**: The frontend SPA loads runtime parameters dynamically from `/spa/config.js` served by `spa-server`, allowing the same build artifact to deploy across development, testing, and production environments without rebuilding.
5. **High Availability Ready**: Redis session clustering and Spring Cloud load balancing enable multi-instance deployments for both `token-generator`, `bff` and `registration`.

---

## 4. Getting Started

### Prerequisites
- **Java**: 25+ JDK
- **Node.js**: 24+ & npm 11+
- **PowerShell**: 7+ (for dev automation scripts)
- **Maven**: 3.10+ (or use the provided wrapper scripts)

### Development Environment Startup
The `dev/` directory provides an all-in-one PowerShell script to launch the entire stack:

```powershell
# Navigate to dev directory
cd dev

# Start all services with automated H2 database setup
./nidam-dev.ps1 start

# Stop all running services
./nidam-dev.ps1 stop
```

Once running, access the application in your browser:
- **Application Frontend**: [http://localhost:7080/spa](http://localhost:7080/spa)
