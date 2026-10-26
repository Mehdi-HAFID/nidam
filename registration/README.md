# Registration Service — Nidam User Provisioning

The **Registration Service** is the user onboarding and account provisioning microservice for the **Nidam** platform. Built 
with **Spring Boot**, it handles new user registrations, validates password complexity, computes cryptographic password hashes
across multiple hashing algorithms, validates bot protection via Google reCAPTCHA, and seeds initial security authorities into
the centralized identity database.

---

## 1. Role in the Nidam Ecosystem

In the Nidam architecture, `registration` serves as the public entry point for account creation:
- **User Account Creation**: Receives signup requests from the frontend SPA (`nidam-spa`), validates payload data, 
and ensures uniqueness of usernames and emails.
- **Cryptographic Credential Storage**: Hashes user passwords using configurable algorithms (`scrypt`, `bcrypt`, `argon2`, `pbkdf2`) 
before storing them in the shared database consumed by `token-generator`.
- **Bot Defense**: Protects registration endpoints from automated abuse by verifying Google reCAPTCHA tokens against Google's verification API.
- **Role and Authority Provisioning**: Automatically initializes and assigns fine-grained authorities (`manage-users`, `manage-projects`) to newly
created user accounts.

```
       [ Browser / SPA ]
              │
 1. Submit    │ (POST /registration/register)
 Registration │
              ▼
   [ Reverse Proxy (:7080) ]
              │
              ▼ (Path: /registration/**)
┌────────────────────────────────────────────────────────┐
│               Registration Service (:4000)             │
│  - User Input & Password Policy Validation             │
│  - Google reCAPTCHA Verification Proxy                 │
│  - Multi-Algorithm Password Hashing                    │
│  - Default Authority Seeding                           │
└────────────────────────────────────────────────────────┘
         │                               │
         ▼ (SQL / Mongo)                 ▼ (Captcha Verify)
[ (H2 / MySQL / Mongo) ]        [ Google reCAPTCHA API ]
         ▲
         │ (Authentication Queries)
[ Token Generator (:4002) ]
```

---

## 2. Core Features & Capabilities

- **Multi-Algorithm Password Hashing**: Encodes credentials using SCrypt, BCrypt, Argon2, or PBKDF2 with standard Spring Security format 
prefixes (e.g., `{bcrypt}...` or `{scrypt}...`) as well as idless formats.
- **Google reCAPTCHA Integration**: Supports optional reCAPTCHA v2 / v3 verification with server-side validation against 
Google's endpoint (`ReCaptchaProxy`).
- **Flexible Data Store Abstraction**:
  - **SQL**: JPA/Hibernate entities supporting H2 (embedded/standalone), MySQL and others.
  - **MongoDB**: Spring Data MongoDB repository support.
- **Authority Initializer**: Automatically creates baseline authorities on startup if absent.
- **Structured Error Handling**: Explicit, localized error payloads for duplicate usernames/emails (`AlreadyExistException`), 
weak/invalid passwords (`PasswordInvalidException`), and failed captcha challenges (`ReCaptchaException`).

---

## 3. Key Endpoints

| HTTP Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/register` | Registers a new user with username, password, and email |
| `POST` | `/registerCaptcha` | Registers a new user with required Google reCAPTCHA token validation |
| `GET` | `/actuator/health` | Health check endpoint |

### Example Registration Request Body
```json
{
  "email": "jane@example.com",
  "password": "SecurePassword123!",
  "recaptchaToken": "03AFcWeA7..."
}
```

---

## 4. Configuration Reference

Key properties in `src/main/resources/application.yml` or overridden via `configuration.yml`:

```yaml
registration-port: 4000

# Persistence: 'sql' or 'mongo'
nidam:
  persistence-mode: "sql"

# Database connection shared with token-generator
users-db-url: jdbc:h2:tcp://localhost:9092/identity_hub
users-db-user: nidam
users-db-password: "gF2mshbI819AV2L3"

# Password encoder preferences
password-encoders: scrypt,bcrypt,argon2,pbkdf2
password-idless: scrypt

# Default authorities assigned to new users
authorities: manage-users,manage-projects

# reCAPTCHA configuration
recaptcha-secret: "6LcyyEMpAAAAANm0qZB6kzDQRT5Li4FSGCTe8ESf"
```

---

## 5. Building and Running

### Prerequisites
- Java 25+ JDK
- Maven 3.10+ (or included `./mvnw`)
- Running Database (included H2 TCP server on port 9092, MySQL on 3306, or MongoDB on 27017)

### Local Development
```bash
# Run with Spring Boot Maven plugin (dev profile)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Or if you have PowerShell 7+ installed, run from the dev/ folder:
.\nidam-dev.ps1 start

# Or package and run the executable JAR in production mode
mvn clean package -DskipTests
java -jar target/registration-*.jar --spring.profiles.active=prod
```

---

## 6. Part of the Nidam Platform
- **[token-generator](../token-generator/README.md)**: Reads user records created by `registration` to authenticate users.
- **[reverse-proxy](../reverse-proxy/README.md)**: Public edge proxy routing `/registration/**` to this service.
- **[bff](../bff/README.md)**: Backend-For-Frontend gateway managing user sessions.
- **[nidam-spa](../nidam-spa/README.md)**: User interface hosting the registration form.
- **[nidam](../nidam/README.md)**: Resource server enforcing authorities assigned during registration.
- **[spa-server](../spa-server/README.md)**: Frontend hosting server.
