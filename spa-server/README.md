# SPA Server — Static Frontend Host & Runtime Configuration Provider

The **SPA Server** (`spa-server`) is a lightweight, non-blocking **Spring Boot (WebFlux)** service in the **Nidam** platform. It serves the compiled static assets of the Single Page Application (`nidam-spa`) and dynamically generates the runtime configuration script (`/spa/config.js`), enabling the frontend to adapt dynamically to varying environment topology without requiring build-time rebundling.

---

## 1. Role in the Nidam Ecosystem

In modern single-page architectures, embedding environment URLs directly into frontend JavaScript bundles during `npm build` compromises portability. The `spa-server` resolves this by decoupling the build artifact from runtime infrastructure:
- **Dynamic Runtime Config Injection**: Evaluates platform parameters from `configuration.yml` and serves them as a dynamic JavaScript file (`GET /spa/config.js`) exposing `window.NIDAM_CONFIG`.
- **SPA Fallback Routing**: Serves static HTML/JS/CSS assets and gracefully handles deep-linked client-side routes by falling back to `spa/index.html`.
- **Zero-Rebuild Deployments**: Allows promoting the exact same static artifact across development, testing, and production environments by simply modifying Spring configuration files or environment variables.

```
       [ Public Client / Browser ]
                    │
                    ▼ (GET /spa/**)
        [ Reverse Proxy (:7080) ]
                    │
                    ▼ (Route: /spa/**)
┌────────────────────────────────────────────────────────┐
│                   SPA Server (:4001)                   │
│  - Spring WebFlux Reactive RouterFunction              │
│  - Dynamic /spa/config.js Generator                    │
│  - Static Asset Delivery from ./spa Directory          │
│  - Single Page Application (SPA) HTML5 Fallback        │
└────────────────────────────────────────────────────────┘
```

---

## 2. Dynamic Configuration Injection (`/spa/config.js`)

When the browser requests `http://localhost:7080/spa/config.js`, `spa-server` dynamically responds with:

```javascript
window.NIDAM_CONFIG = {
    BACKEND_REGISTRATION_URL: "http://localhost:7080/registration",
    BASE_URI: "http://localhost:7080/spa",
    RESOURCE_SERVER_URI: "http://localhost:7080/bff/api",
    LOGIN_URL: "http://localhost:7080/bff/oauth2/authorization/token-generator",
    LOGOUT_URL: "http://localhost:7080/bff/logout",
    PROFILE_PUBLIC_ENDPOINT: "/me",
    BASE_PATH: "/spa",
    RECAPTCHA_ENABLED: "false",
    RECAPTCHA_KEY: "6LcyyEMpAAAAAMztnW6xVq1HFD0b-mlyk2t6NZa-"
};
```

---

## 3. Key Endpoints

| HTTP Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/spa/config.js` | Dynamically generated configuration script |
| `GET` | `/spa/**` | Static files (JS, CSS, images) with fallback to `spa/index.html` |

---

## 4. Configuration Reference

Key properties in `src/main/resources/application.yml` or overridden via `configuration.yml`:

```yaml
server:
  port: 4001

react-port: 4001
react-prefix: /spa
reverse-proxy-uri: http://localhost:7080

registration-prefix: /registration
registration-uri: ${reverse-proxy-uri}${registration-prefix}

bff-prefix: /bff
bff-resource-server-route-prefix: /api
bff-proxy-uri: ${reverse-proxy-uri}${bff-prefix}
resource-server-through-bff-proxy-uri: ${bff-proxy-uri}${bff-resource-server-route-prefix}

react-login-url: ${bff-proxy-uri}/oauth2/authorization/token-generator
react-logout-url: ${bff-proxy-uri}/logout

profile-public-endpoint: /me
recaptcha-enabled: 'false'
recaptcha-key: 6LcyyEMpAAAAAMztnW6xVq1HFD0b-mlyk2t6NZa-
```

---

## 5. Building and Running

### Prerequisites
- Java 25+ JDK
- Maven 3.10+ (or included `./mvnw`)
- Compiled frontend build placed in `spa/` (from `nidam-spa`)

### Local Development
```bash
# Run with Spring Boot Maven plugin (dev profile)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Or if you have PowerShell 7+ installed, run from the dev/ folder:
.\nidam-dev.ps1 start

# Or package and run the executable JAR in production mode
mvn clean package -DskipTests
java -jar target/spa-server-*.jar --spring.profiles.active=prod
```

---

## 6. Part of the Nidam Platform
- **[nidam-spa](../nidam-spa/README.md)**: React source code compiled and served by this host.
- **[reverse-proxy](../reverse-proxy/README.md)**: Edge proxy routing `/spa/**` traffic to this service.
- **[bff](../bff/README.md)**: Backend-For-Frontend gateway referenced in the dynamic login/logout configuration.
- **[registration](../registration/README.md)**: User registration service referenced in the configuration.
- **[token-generator](../token-generator/README.md)**: Authorization server powering the authentication flow.
- **[nidam](../nidam/README.md)**: Protected resource server.
