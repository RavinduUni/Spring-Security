# 🔐 Spring Security JWT — Complete Request Flow Guide

> A plain-English walkthrough of how every request travels through this application, what each class does, and how JWT stateless security differs from classic session-based security.

---

## 📑 Table of Contents

1. [Project Overview](#1-project-overview)
2. [The Big Picture — What Happens to Every Request?](#2-the-big-picture--what-happens-to-every-request)
3. [Layer by Layer — The Spring Filter Chain](#3-layer-by-layer--the-spring-filter-chain)
4. [The Dispatcher Servlet](#4-the-dispatcher-servlet)
5. [Detailed Flow: Registering a User (POST /create)](#5-detailed-flow-registering-a-user-post-create)
6. [Detailed Flow: Logging In (POST /login)](#6-detailed-flow-logging-in-post-login)
7. [Detailed Flow: Accessing a Protected Endpoint (GET /hello)](#7-detailed-flow-accessing-a-protected-endpoint-get-hello)
8. [Class-by-Class Breakdown](#8-class-by-class-breakdown)
9. [Method-by-Method: JWTService](#9-method-by-method-jwtservice)
10. [Stateful vs Stateless Security](#10-stateful-session-security-vs-stateless-jwt-security)
11. [Quick Reference: Endpoints](#11-quick-reference-which-endpoints-need-a-token)

---

## 1. Project Overview

This is a **Spring Boot REST API** secured with **JWT (JSON Web Tokens)**. Instead of keeping a login session on the server, the server gives the client a **signed token** after login. The client sends that token with every future request to prove who they are.

**Tech Stack:**
- Spring Boot 4.x
- Spring Security
- Spring Data JPA + MySQL
- JJWT (Java JWT library)
- Lombok

---

## 2. The Big Picture — What Happens to Every Request?

Think of your application as a building with a security checkpoint at the entrance. Every person (request) who tries to enter must pass through that checkpoint before reaching anyone inside (your controllers).

```
CLIENT (Browser / Postman)
        |
        |  HTTP Request (with or without "Authorization: Bearer <token>")
        v
+-------------------------------------------------------------+
|                  SERVLET CONTAINER (Tomcat)                  |
|                                                             |
|  +------------------------------------------------------+   |
|  |              SPRING SECURITY FILTER CHAIN            |   |
|  |                                                      |   |
|  |  Filter 1: CorsFilter                                |   |
|  |  Filter 2: SecurityContextPersistenceFilter          |   |
|  |  Filter 3: >>> JWTFilter (OUR CUSTOM FILTER) <<<    |   |
|  |  Filter 4: UsernamePasswordAuthenticationFilter      |   |
|  |  Filter 5: ExceptionTranslationFilter                |   |
|  |  Filter 6: FilterSecurityInterceptor                 |   |
|  +------------------------------------------------------+   |
|                          |                                   |
|                          v                                   |
|            DispatcherServlet (Front Controller)              |
|                          |                                   |
|                          v                                   |
|                    @RestController                           |
|           (HelloController / StudentController)              |
+-------------------------------------------------------------+
        |
        |  HTTP Response (JSON data or JWT token)
        v
      CLIENT
```

---

## 3. Layer by Layer — The Spring Filter Chain

### What is a Filter?

A **Filter** is code that runs **before** your request reaches any controller, and also **after** the response leaves the controller. Spring Security uses a chain of filters — each one does a specific job.

Think of it like a conveyor belt with checkpoints. The request slides along, and each checkpoint either:
- Lets it through to the next one
- Stops it and sends back an error response

### The Key Filters in This App

```
Request --> [CorsFilter] --> [JWTFilter] --> [AuthorizationFilter] --> DispatcherServlet
```

| Filter | What It Does |
|--------|-------------|
| `CorsFilter` | Handles Cross-Origin requests (allows requests from other domains) |
| **`JWTFilter`** | **Our custom filter** — reads and validates the JWT token |
| `UsernamePasswordAuthenticationFilter` | Handles form login (not used here, JWT goes before this) |
| `ExceptionTranslationFilter` | Catches security exceptions and converts them to 401/403 responses |
| `AuthorizationFilter` | Checks if the authenticated user has permission to access the URL |

### How `filterChain.doFilter()` Works

Every filter must call `filterChain.doFilter(request, response)` at the end to pass the request to the **next** filter. If a filter does NOT call this (like when we return early with a 401), the request stops right there and never reaches the controller.

```java
// JWTFilter.java
filterChain.doFilter(request, response);  // "pass it on to the next filter"
```

---

## 4. The Dispatcher Servlet

The **DispatcherServlet** is the main entry point of a Spring MVC application. It is Spring's "traffic director."

```
Request arrives at DispatcherServlet
         |
         v
  Looks at the URL path
  (e.g., GET /hello, POST /login)
         |
         v
  Finds the matching @Controller method
  using HandlerMapping
         |
         v
  Calls that controller method
         |
         v
  Converts the return value to JSON
  using HttpMessageConverter
         |
         v
  Sends HTTP Response back to client
```

**Important:** The DispatcherServlet only gets the request **after all security filters have passed**. If `JWTFilter` rejects a request (no token, bad token), it never even reaches the DispatcherServlet.

---

## 5. Detailed Flow: Registering a User (POST /create)

This endpoint is **public** — no token needed. Anyone can register.

```
POST /create
Body: { "name": "Alice", "password": "secret123", "tech": "Java" }
```

**Step-by-step:**

```
1. Request hits Tomcat
       |
2. JWTFilter runs
   - No "Authorization" header found
   - userName stays null
   - Calls filterChain.doFilter() --> passes through [OK]
       |
3. AuthorizationFilter runs
   - URL "/create" is in permitAll() list --> allowed [OK]
       |
4. DispatcherServlet routes to StudentController.register()
       |
5. StudentController.register()
   - Calls StudentService.register(student)
       |
6. StudentService.register()
   - Encodes the password with BCrypt:
     "secret123" --> "$2a$10$Kx3B..."
   - Saves student to MySQL database
   - Generates a JWT token for the new user
       |
7. JWTService.generateToken("Alice")
   - Creates a signed JWT with:
     subject: "Alice"
     issuedAt: now
     expiration: now + 30 minutes
     signed with HMAC-SHA256 secret key
       |
8. Response sent back to client:
   {
     "Student": { "id": 1, "name": "Alice", "tech": "Java" },
     "token": "eyJhbGci..."
   }
```

---

## 6. Detailed Flow: Logging In (POST /login)

This endpoint is also **public**. The user sends their credentials and gets a JWT back.

```
POST /login
Body: { "name": "Alice", "password": "secret123" }
```

**Step-by-step:**

```
1. Request hits Tomcat
       |
2. JWTFilter runs
   - No "Authorization" header --> passes through [OK]
       |
3. AuthorizationFilter runs
   - "/login" is in permitAll() --> allowed [OK]
       |
4. DispatcherServlet --> StudentController.login()
       |
5. StudentService.login()
   - Calls authenticationManager.authenticate(
         new UsernamePasswordAuthenticationToken("Alice", "secret123")
     )
       |
6. AuthenticationManager delegates to DaoAuthenticationProvider
   - Calls MyUserDetailsService.loadUserByUsername("Alice")
       - Queries DB: SELECT * FROM student WHERE name = 'Alice'
       - Returns UserPrincipal(alice)
   - Compares "secret123" with the BCrypt hash from DB
   - If match --> authentication succeeds [OK]
       |
7. StudentService generates a new JWT token
   - JWTService.generateToken("Alice")
       |
8. Response: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOi..."  (the JWT token string)
```

> The client MUST save this token and send it with every future protected request.

---

## 7. Detailed Flow: Accessing a Protected Endpoint (GET /hello)

This is where JWT actually does its job. The client sends the token it received from login.

```
GET /hello
Headers: Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Step-by-step:**

```
1. Request hits Tomcat
       |
2. JWTFilter runs:
   a) Read header: "Authorization: Bearer eyJ..."
   
   b) Extract token = "eyJhbGciOiJIUzI1NiJ9..."
   
   c) Try to extract username from token:
      jwtService.extractUserName(token)
      --> Decodes JWT --> reads "sub" claim --> "Alice"
   
   d) If token is malformed/expired:
      --> sendError(401) + return [STOPS HERE - never reaches controller]
   
   e) username = "Alice", no existing auth in SecurityContext
   
   f) Load user from DB:
      MyUserDetailsService.loadUserByUsername("Alice")
      --> Returns UserPrincipal with roles
   
   g) Validate token:
      jwtService.validateToken(token, userDetails)
      --> username matches [OK] AND token not expired [OK]
   
   h) Set authentication in SecurityContext:
      SecurityContextHolder.getContext().setAuthentication(authToken)
      Spring now knows "Alice is authenticated"
   
   i) Call filterChain.doFilter() --> pass to next filter [OK]
       |
3. AuthorizationFilter runs
   - SecurityContext has authenticated user "Alice"
   - "/hello" requires authentication --> Alice is authenticated [OK]
       |
4. DispatcherServlet --> HelloController.greet()
       |
5. HelloController.greet()
   - Reads "Alice" from SecurityContextHolder
   - Returns: "Hello, Alice!"
       |
6. Response: 200 OK  "Hello, Alice!"
```

---

## 8. Class-by-Class Breakdown

### `SecurityConfig.java` — The Security Blueprint

This is where you **configure all security rules**. Spring reads this at startup.

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig { ... }
```

| Method | What It Does |
|--------|-------------|
| `authenticationProvider()` | Tells Spring: "Use the DB to look up users, use BCrypt to check passwords" |
| `securityFilterChain()` | Defines which URLs are public, disables sessions, registers JWTFilter |
| `passwordEncoder()` | Creates a BCryptPasswordEncoder bean for hashing and verifying passwords |
| `authenticationManager()` | Exposes AuthenticationManager so `StudentService` can use it to log users in |

**Key decisions made here:**

```java
// Disable CSRF — not needed for stateless REST APIs
http.csrf(csrf -> csrf.disable());

// /create and /login are public, everything else needs authentication
http.authorizeHttpRequests(request ->
    request.requestMatchers("/create", "/login").permitAll()
           .anyRequest().authenticated());

// STATELESS — don't create HTTP sessions
http.sessionManagement(session ->
    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

// Run JWTFilter BEFORE Spring's built-in login filter
http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
```

---

### `JWTFilter.java` — The Token Inspector

Runs on **every single request**. Its only job is to check if a valid JWT token is present and, if so, set the authenticated user in the SecurityContext.

```
Incoming Request
      |
      v
Does it have "Authorization: Bearer ..." header?
      |
   YES|                              NO|
      v                               v
Extract token                    Skip (no token present)
      |                               |
      v                               |
Parse token, get username             |
      |                               |
  Error?-----------------------------> 401 Unauthorized (return early)
      |
      v
Load user from DB
      |
      v
Is token valid? (username matches AND not expired)
      |
   YES|
      v
Set Authentication in SecurityContext
      |
      v (both paths merge here)
filterChain.doFilter() --> next filter
```

---

### `JWTService.java` — The Token Factory

Creates, signs, parses, and validates JWT tokens.

---

### `MyUserDetailsService.java` — The User Loader

Spring Security calls this when it needs to **load a user from the database by username**.

```java
public UserDetails loadUserByUsername(String username) {
    Student user = repository.findByName(username);  // DB query
    if (user == null) throw new UsernameNotFoundException("User not found");
    return new UserPrincipal(user);  // wrap in Spring Security's UserDetails
}
```

Spring Security does not know about your `Student` entity — it only works with `UserDetails`. `UserPrincipal` is the adapter that bridges them.

---

### `UserPrincipal.java` — The Security Wrapper

Wraps your `Student` entity so Spring Security can use it.

```java
public class UserPrincipal implements UserDetails {
    // Spring calls these methods:
    getUsername()     --> returns student.getName()
    getPassword()     --> returns student.getPassword() (BCrypt hash)
    getAuthorities()  --> returns ["USER"] role
}
```

---

### `StudentService.java` — Business Logic

| Method | What It Does |
|--------|-------------|
| `register()` | Hashes password, saves to DB, generates JWT, returns both |
| `login()` | Uses AuthenticationManager to verify credentials, generates JWT |
| `getAllStudents()` | Returns all students from DB |

---

### `StudentController.java` + `HelloController.java` — The Endpoints

These only run **after** security has been cleared. They contain your actual business responses.

| Endpoint | Auth Required | What Happens |
|----------|--------------|-------------|
| `POST /create` | No | Registers a new user, returns JWT |
| `POST /login` | No | Verifies credentials, returns JWT |
| `GET /students` | Yes | Returns all students from DB |
| `GET /hello` | Yes | Returns greeting with your username |

---

## 9. Method-by-Method: JWTService

### `generateToken(String username)`

Builds and signs a JWT token.

```
username = "Alice"
      |
      v
Create empty claims map {}
      |
      v
Build JWT:
  Header:  { "alg": "HS256", "typ": "JWT" }
  Payload: {
    "sub": "Alice",        <- subject (who the token is for)
    "iat": 1698329400,     <- issued at (Unix timestamp)
    "exp": 1698331200      <- expires at (30 minutes later)
  }
  Signature: HMAC-SHA256(Base64(header) + "." + Base64(payload), secretKey)
      |
      v
Compact to: "eyJhbGci....eyJzdWIi....Signature"
             |--header--|--payload--|--signature--|
```

The three parts are Base64-encoded and joined with dots. **Anyone can decode the header and payload** (they are not encrypted!) but they **cannot forge the signature** without the secret key — that is the whole security model.

---

### `validateToken(String token, UserDetails userDetails)`

```java
public boolean validateToken(String token, UserDetails userDetails) {
    final String userName = extractUserName(token);          // get "Alice" from token
    return (userName.equals(userDetails.getUsername())       // "Alice" == "Alice" [OK]
            && !isTokenExpired(token));                       // exp > now [OK]
}
```

Two checks:
1. **Username matches** — the token is actually for this user
2. **Token not expired** — the expiry date is still in the future

---

### `extractUserName()` chain

These three methods form a chain:

```
extractUserName(token)
    --> extractClaim(token, Claims::getSubject)
            --> extractAllClaims(token)
                    --> Jwts.parser()
                              .verifyWith(secretKey)    <- verifies signature
                              .build()
                              .parseSignedClaims(token) <- decodes JWT
                              .getPayload()             <- returns Claims object
                    --> claimResolver.apply(claims)
                              --> claims.getSubject()   <- returns "Alice"
```

---

## 10. Stateful (Session) Security vs Stateless (JWT) Security

### The Classic Stateful Way

In **traditional Spring Security** (form login + sessions), the server **remembers** who is logged in.

```
STATEFUL FLOW:

1. Client: POST /login { username, password }
                |
2. Server verifies credentials [OK]
                |
3. Server creates an HTTP Session in memory:
   Sessions = {
     "JSESSIONID=abc123" --> { user: "Alice", roles: ["USER"] }
   }
                |
4. Server sends back Cookie:
   Set-Cookie: JSESSIONID=abc123
                |
5. Browser automatically sends the cookie on EVERY future request:
   GET /hello
   Cookie: JSESSIONID=abc123
                |
6. Server looks up "abc123" in its session store --> finds "Alice"
   --> Alice is authenticated --> lets her through
```

**Problems with Stateful:**
- Server must store sessions in memory (or Redis, DB) — uses RAM per user
- Does not scale well across multiple servers — if you have 3 servers, the session is only on server #1. Requests that go to server #2 have no idea who you are
- Every server needs shared session storage to scale horizontally
- CSRF attacks are possible since browsers auto-send cookies to any website

---

### The Stateless JWT Way

With JWT, the server **remembers nothing**. The client carries everything it needs.

```
STATELESS JWT FLOW:

1. Client: POST /login { username, password }
                |
2. Server verifies credentials [OK]
                |
3. Server creates a signed JWT token:
   "eyJhbGci...Alice...exp:30min...Signature"
   
   Server stores NOTHING. It gives the token to the client and forgets.
                |
4. Server sends back: { "token": "eyJhbGci..." }
                |
5. Client stores the token (in localStorage, memory, etc.)
                |
6. Client manually sends the token on every protected request:
   GET /hello
   Authorization: Bearer eyJhbGci...
                |
7. Server receives token --> verifies signature --> reads username from token
   --> No DB or session lookup needed --> lets the client through
```

**Benefits of Stateless:**
- Server stores nothing — zero memory overhead per logged-in user
- Scales perfectly — any server can verify any token since they all share the same secret key
- Works across domains — no cookie means no CSRF risk
- Great for mobile apps, REST APIs, and microservices

---

### Side-by-Side Comparison

| Feature | Stateful (Session) | Stateless (JWT) |
|---------|-------------------|-----------------|
| Server stores login state? | YES (in memory/DB) | NO |
| Client sends on each request | Cookie (automatic by browser) | Authorization: Bearer header (manual) |
| Scales horizontally? | Hard (shared session store needed) | Easy (any server verifies) |
| Logout | Easy — just delete the server session | Hard — token stays valid until expiry |
| Token expiry | Server controls session lifetime | Baked into the token itself |
| CSRF risk | YES (cookies are auto-sent by browsers) | NO (Authorization header is not auto-sent) |
| Best for | Traditional web apps (Thymeleaf, JSP) | REST APIs, Mobile apps, Microservices |

---

### Why This App Uses `SessionCreationPolicy.STATELESS`

In this app we explicitly tell Spring Security "never create an HTTP session":

```java
http.sessionManagement(session ->
    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
```

Without this, Spring Security might still create a session "just in case," making the app accidentally stateful. This line enforces a strict no-session policy.

---

### The Logout Problem with JWT

One thing stateless JWT does not handle well is **logout**. Since the token is self-contained and the server stores nothing, you cannot simply "delete the session." The token stays valid until it expires.

Common solutions:
- **Short expiry** — this app uses 30 minutes
- **Token blacklist** — store revoked tokens in Redis, but this adds state back
- **Refresh token pattern** — short-lived access tokens plus long-lived refresh tokens

---

## 11. Quick Reference: Which Endpoints Need a Token?

| Method | URL | Token Required | What It Does |
|--------|-----|---------------|-------------|
| `POST` | `/create` | No | Register new user, get JWT back |
| `POST` | `/login` | No | Login with credentials, get JWT back |
| `GET` | `/students` | Yes | Get all students from DB |
| `GET` | `/hello` | Yes | Get greeting with your username |

### How to Use the Token in Postman / REST Client

```
# Step 1: Login
POST http://localhost:8080/login
Content-Type: application/json
Body: { "name": "Alice", "password": "secret123" }

Response: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJBbGljZSIs..."

# Step 2: Use the token on protected endpoints
GET http://localhost:8080/hello
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJBbGljZSIs...

Response: "Hello, Alice!"
```

---

*Generated for the `spring-security-test` project — a Spring Boot JWT demonstration application.*
