# React SPA — Nidam Single Page Application

The **Nidam SPA** (`nidam-spa`) is the official responsive web frontend for the **Nidam** platform. Built with **Create-React-App** **React 18**, **Redux Toolkit**, **Redux-Saga**, and **React Router v6**, it delivers a seamless user experience for registration, single sign-on (SSO), profile inspection, and interacting with protected backend APIs through the **BFF (Backend For Frontend)**.

---

## 1. Role in the Nidam Ecosystem

In the Nidam architecture, `nidam-spa` serves as the client presentation layer:
- **Zero Token Storage**: Complies with the **Token Handler Pattern**—it never receives, stores, or handles OAuth 2.0 access or refresh tokens in `localStorage`, `sessionStorage`, or JavaScript memory.
- **Secure Cookie Sessions**: Communicates with the `bff` using standard `SameSite` HTTP-only session cookies.
- **Dynamic Runtime Configuration**: Loads environment-specific backend URLs at runtime from `/spa/config.js` via `window.NIDAM_CONFIG`, avoiding hardcoded build variables.
- **Authentication Lifecycle**:
  - Directs unauthenticated users to `/bff/oauth2/authorization/token-generator` for federated login.
  - Evaluates session state during startup by calling `/bff/api/me` while presenting a smooth splash screen.
  - Initiates complete logout via `/bff/logout`.

```
┌────────────────────────────────────────────────────────┐
│                   Nidam SPA (React)                    │
│  - Redux Toolkit + Redux-Saga Side Effects             │
│  - Dynamic Runtime Config (window.NIDAM_CONFIG)        │
│  - Form Validation & Google reCAPTCHA Integration      │
│  - Protected Route Navigation & Splash Screen Probing  │
└────────────────────────────────────────────────────────┘
          │ (POST /registration/register)
          ├────────────────────────────────────────┐
          │ (Cookie-authenticated API / Auth)      │
          ▼                                        ▼
[ Reverse Proxy (:7080) ]               [ Reverse Proxy (:7080) ]
          │                                        │
          ▼ (Path: /bff/**)                        ▼ (Path: /registration/**)
[ BFF Gateway (:7081) ]                 [ Registration Service (:4000) ]
```

---

## 2. Core Features & Capabilities

- **Redux-Saga State Management**: Asynchronous action dispatching and side-effect handling for authentication state (`authenticationSlice`, `authentication.js`), user registration (`registerSlice`, `registerSaga.js`), and API requests (`demoSlice`, `demoSaga.js`).
- **Runtime Environment Injection**: Reads configuration dynamically via `src/config.js` from `window.NIDAM_CONFIG`:
  - `BACKEND_REGISTRATION_URL`: Registration microservice endpoint.
  - `RESOURCE_SERVER_URI`: Base URL for proxied resource calls (`/bff/api`).
  - `LOGIN_URL` / `LOGOUT_URL`: BFF authentication trigger routes.
  - `RECAPTCHA_ENABLED` / `RECAPTCHA_KEY`: Bot prevention toggles.
- **User Onboarding UI**: Interactive signup screen (`SignUp.js`) with client-side form validation, password requirements checking, and optional Google reCAPTCHA challenge.
- **Authenticated Dashboard (`Private.js`)**: Real-time display of user identity, assigned security authorities (`manage-users`, `manage-projects`), token expiration timer, and demo resource triggers (`/bff/api/demo`).

---

## 3. Application Structure

```
src/
├── authentication/       # Startup session probing & auth handlers
├── container/            # Primary view containers (Login, Logout, SignUp, Private)
├── pages/                # Splash screen, error pages (404, 500)
├── redux/
│   ├── authentication/   # Redux slice & Sagas for user session lifecycle
│   ├── register/         # Redux slice & Sagas for user account creation
│   ├── nidam/            # Redux slice & Sagas for resource server interactions
│   └── store.js          # Root Redux store configuration
├── routing/              # App routing definitions (React Router v6)
├── config.js             # Runtime configuration loader (window.NIDAM_CONFIG)
└── index.js              # Application entry point with BrowserRouter (/spa)
```

---

## 4. Building and Running

### Prerequisites
- Node.js 24+
- npm 11+

### Local Development
```bash
# 1. Install dependencies
npm install

# 2. Start local React development server
npm start
```
By default, the development server runs on `http://localhost:4001` with basename `/spa` (or `/react-ui`). But you should **Never** access the SPA directly, access should always be through the reverse proxy (for example http://localhost:7080/spa)

### Production Build
```bash
# Compile optimized static bundle
npm run build
```
The output `build/` directory can be served via Nginx or placed into the `spa/` folder of `spa-server`.

---

## 5. Part of the Nidam Platform
- **[spa-server](../spa-server/README.md)**: WebFlux server that hosts the compiled SPA assets and serves dynamic `/spa/config.js`.
- **[bff](../bff/README.md)**: Handles OAuth 2.0 login/logout and token injection on behalf of this SPA.
- **[reverse-proxy](../reverse-proxy/README.md)**: Front-door gateway serving `/spa/**` to users.
- **[token-generator](../token-generator/README.md)**: Identity provider where users authenticate.
- **[registration](../registration/README.md)**: Backend endpoint used for new account registration.
- **[nidam](../nidam/README.md)**: Protected resource server queried by this UI via the BFF.
