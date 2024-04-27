Nidam is a collection of Spring OAuth 2 microservices and a React SPA that takes care of Registration and Authentication of Users using 
the Security Standard OAuth 2. So You only focus on Business Logic.

visit: https://nidam.derbyware.com

# What is Nidam

You want to start coding a new spring application but first registration and authentication are required before you start writing your backend
and front code.

Nidam solves this problem using the standard **OAuth 2**, by providing:
- Registration backend connected to a **MySQL** database for credentials storage.
- Registration React front page to interface with registration API.
- Registration comes with _Google Recaptcha_.
- **Spring Authorization Server** backend that's responsible for the login process and generating the token.
- **Spring OAuth 2 Resource Server** backend, is where your code lives.
- React front initiates login, logout, and access to secured resource server endpoint, this is where your front code lives.

Now this is actually not enough for an OAuth2 secured application to work. See the front code is not allowed to receive
and save the token for subsequent API calls. We need to implement what is called **Backend For Frontend (BFF)** pattern.
This is a **Spring OAuth2 Client** that receives and saves tokens. It is a bridge between the front and the resource server.
Upon successful login, the token is sent to this BFF where it is stored securely.
Before the BFF redirects to the front end, it generates a **SESSION cookie** for the front when redirected.
When the front wants to make a call to the resource server, it always goes through the BFF that replaces the cookie with the _token_ associated with it
and calls the resource server, which validates the token and returns the result for the front.

The BFF implemented by Nidam is taken from this baeldung article
[OAuth2 Backend for Frontend With Spring Cloud Gateway](https://www.baeldung.com/spring-cloud-gateway-bff-oauth2) by **Jérôme Wacongne**

So for the BFF, we need two backend microservices:
- **Reverse Proxy**: the front, the authorization server, and the BFF live behind the reverse proxy.
  We need the front and the BFF to have the _same origin_ so the SESSION cookie works.
  Serving the authorization server behind the reverse proxy is an implementation choice.
- **BFF (backend for frontend)**: as explained above, this is a bridge between the front and resource server to translate the SESSION
  cookie to the token.

Requirements:
- [Spring Security](https://docs.spring.io/spring-security/reference/index.html)
- [Spring OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html) and
  [Spring OAuth2 Client](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html)
- [Spring Authorization Server](https://docs.spring.io/spring-authorization-server/reference/index.html)

This book covers Spring Security OAuth2: link

Nidam will not teach you these. This documentation will only explain how it uses them to achieve its goal.

Nidam uses React as the front framework, you can replace this with any SPA framework.

Nidam uses **Java 17** version and **React 18**.

Each microservice is hosted on its own repository.

