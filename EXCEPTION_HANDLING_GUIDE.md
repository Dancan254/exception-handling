# Spring Boot Exception Handling — Architecture & Patterns Guide

This guide walks through the exception handling architecture used in this branch, explains every design decision, and surveys the alternative patterns available in Spring Boot — including the RFC 9457 `ProblemDetail` approach that many modern APIs are converging on — so you can make an informed choice in your own applications.

> **What this branch implements:** A custom `ErrorResponse` DTO returned by a plain `@RestControllerAdvice`. This is the most common approach in production Spring Boot codebases and the clearest starting point for learning exception handling. Read [Section 5](#5-upgrading-to-problemdetail-rfc-9457) to understand when and how to upgrade to `ProblemDetail`.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [The Exception Classes](#2-the-exception-classes)
3. [The GlobalExceptionHandler Deep Dive](#3-the-globalexceptionhandler-deep-dive)
4. [The ErrorResponse DTO](#4-the-errorresponse-dto)
5. [Upgrading to ProblemDetail (RFC 9457)](#5-upgrading-to-problemdetail-rfc-9457)
6. [Other Alternative Patterns](#6-other-alternative-patterns)
7. [When to Use What](#7-when-to-use-what)
8. [Common Pitfalls](#8-common-pitfalls)

---

## 1. Architecture Overview

### Request-to-Response Flow

```
HTTP Request
     │
     ▼
┌────────────────────┐
│     Controller     │  Validates input (@Valid), delegates to service
└────────┬───────────┘
         │ calls
         ▼
┌────────────────────┐
│      Service       │  Applies business rules, throws typed exceptions
└────────┬───────────┘
         │ throws RuntimeException subtype
         ▼
┌────────────────────────────────┐
│     GlobalExceptionHandler     │  @RestControllerAdvice
│                                │
│  @ExceptionHandler per type:   │
│  handleNotFound()         404  │
│  handleConflict()         409  │
│  handleBusinessRule()     422  │
│  handleForbidden()        403  │
│  handleGone()             410  │
│  handleExternalService()  502  │
│  handleResponseStatus()   any  │
│  handleValidation()       400  │
│  handleDataIntegrity()    409  │
│  handleAll()              500  │
└────────────────────────────────┘
         │ builds
         ▼
┌────────────────────┐
│   ErrorResponse    │  Custom JSON body
│  {                 │
│    "status": 404,  │
│    "error": "...", │
│    "message":"...",│
│    "path": "...",  │
│    "timestamp":"." │
│  }                 │
└────────────────────┘
         │
         ▼
    HTTP Response
```

### Layer Responsibilities

| Layer | Responsibility |
|---|---|
| **Controller** | Input validation via `@Valid`; delegates all business logic to the service |
| **Service** | Applies business rules; throws domain-specific exceptions |
| **Exception classes** | Carry only the message; each concrete type implies an HTTP status |
| **GlobalExceptionHandler** | Single place that maps every exception type to an `ErrorResponse` |
| **ErrorResponse** | The JSON contract returned to clients for every non-2xx response |

The key principle: **exceptions carry the problem description, the handler decides the HTTP status and response shape.** No HTTP concerns live in the service; no business logic lives in the handler.

---

## 2. The Exception Classes

### Flat Hierarchy

This branch keeps the exception hierarchy as flat as possible — every exception extends `RuntimeException` directly:

```
RuntimeException
    ├── ResourceNotFoundException   (→ 404 Not Found)
    ├── ResourceGoneException       (→ 410 Gone)
    ├── ConflictException           (→ 409 Conflict)
    ├── BusinessRuleException       (→ 422 Unprocessable Entity)
    ├── ForbiddenException          (→ 403 Forbidden)
    └── ExternalServiceException    (→ 502 Bad Gateway)
```

Each class is minimal — it only defines a constructor that builds the message:

```java
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, String field, Object value) {
        super(String.format("%s with %s '%s' was not found", resource, field, value));
    }
}
```

The HTTP status lives in the handler, not the exception. This is a deliberate choice: the exception says *what went wrong*; the handler decides *how to express that to the client*.

### Why `RuntimeException` and Not `Exception`?

Checked exceptions (`Exception`) force every calling method to declare `throws` or catch the exception. In a layered Spring application that adds enormous noise — services, repositories, and controllers would all need try/catch or `throws` declarations just to let the exception reach the global handler. `RuntimeException` propagates freely up the call stack without that ceremony.

### Exception Chaining

`ExternalServiceException` always accepts a `cause` parameter:

```java
public class ExternalServiceException extends RuntimeException {
    public ExternalServiceException(String serviceName, String message, Throwable cause) {
        super(String.format("[%s] %s", serviceName, message), cause);
    }
}
```

In the controller, the original exception is never swallowed:

```java
try {
    simulateExternalPricingCall(product.sku());
} catch (RuntimeException e) {
    throw new ExternalServiceException("PricingService", "Failed to retrieve live pricing data", e);
}
```

Without chaining, the log would only show `ExternalServiceException`. With it, the full chain — including the original timeout or connection message — appears, making incidents significantly easier to diagnose.

### When to Introduce a Base Class

The flat hierarchy works well here because the handler registers a separate `@ExceptionHandler` for each type. If you find yourself adding many exception types and the handler grows repetitive, consider extracting an abstract `AppException` base class that carries the target `HttpStatus`:

```java
public abstract class AppException extends RuntimeException {
    private final HttpStatus status;

    protected AppException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
```

The handler then collapses to a single method:

```java
@ExceptionHandler(AppException.class)
public ResponseEntity<ErrorResponse> handleAppException(AppException ex, HttpServletRequest request) {
    return ResponseEntity.status(ex.getStatus())
        .body(ErrorResponse.of(ex.getStatus(), ex.getMessage(), request.getRequestURI()));
}
```

This is the **Open/Closed Principle** applied to exception handling — new subtypes require zero changes to the handler. The trade-off: the HTTP status is buried in each exception's constructor rather than visible at a glance in the handler.

---

## 3. The GlobalExceptionHandler Deep Dive

### One Handler Per Exception Type

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.error("Resource not found at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI()));
    }

    // ... one method per exception type ...
}
```

The explicit-per-type style has two advantages over a single catch-all:
- The HTTP status for each exception type is visible at a glance.
- You can add type-specific logging levels — `log.error` for 5xx, `log.warn` for 4xx.

### Handling Validation Errors

`MethodArgumentNotValidException` carries a list of `FieldError` objects. The handler collects them into a map and passes it to the full `ErrorResponse` constructor:

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ErrorResponse> handleValidation(
        MethodArgumentNotValidException ex, HttpServletRequest request) {

    Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
        .collect(Collectors.toMap(
            FieldError::getField,
            fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
            (first, second) -> first + "; " + second   // merge duplicate field messages
        ));

    ErrorResponse body = new ErrorResponse(
        HttpStatus.BAD_REQUEST.value(),
        "Bad Request",
        "One or more request fields failed validation.",
        request.getRequestURI(),
        LocalDateTime.now(),
        fieldErrors
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
}
```

The merge function `(first, second) -> first + "; " + second` handles the edge case where the same field has multiple constraint violations.

### Handler Resolution Priority

When an exception is thrown, Spring evaluates `@ExceptionHandler` methods in this order:

1. **Most specific type first** — if `ResourceNotFoundException` has its own handler, that beats `@ExceptionHandler(RuntimeException.class)`.
2. **Declared in the same class first** — handlers in your `@ControllerAdvice` take priority over inherited ones.
3. **`@ExceptionHandler(Exception.class)` last** — the catch-all. Never place it before more specific ones.

```
Exception thrown
      │
      ├── Is it a ResourceNotFoundException? → handleNotFound()
      ├── Is it a ConflictException?         → handleConflict()
      ├── Is it a BusinessRuleException?     → handleBusinessRule()
      ├── Is it a ForbiddenException?        → handleForbidden()
      ├── Is it a ResourceGoneException?     → handleGone()
      ├── Is it an ExternalServiceException? → handleExternalService()
      ├── Is it a ResponseStatusException?   → handleResponseStatus()
      ├── Is it a MethodArgNotValid?         → handleValidation()
      ├── Is it a DataIntegrityViolation?    → handleDataIntegrity()
      └── Anything else                      → handleAll() [catch-all 500]
```

### The Catch-All Handler

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleAll(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception at {}", request.getRequestURI(), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse.of(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again later.",
            request.getRequestURI()
        ));
}
```

Two things are intentional:
- **Full stack trace is logged** — `log.error(..., ex)` passes the exception as the last argument, which SLF4J renders as a full stack trace.
- **No internal detail is exposed to the client.** The message is a fixed safe string. Never return `ex.getMessage()` from a catch-all — it can leak class names, SQL queries, or file paths.

### Spring MVC Internal Exceptions

Because this handler does **not** extend `ResponseEntityExceptionHandler`, Spring MVC internal exceptions (method not allowed, unsupported media type, missing request header, etc.) are not handled by your code. They fall through to Spring Boot's default `/error` endpoint, which returns a different JSON shape from your `ErrorResponse`.

For most internal APIs this is acceptable. If you need all errors — including Spring MVC internals — to use your `ErrorResponse` shape, either:
- Add explicit `@ExceptionHandler` methods for each Spring MVC exception you care about (`HttpRequestMethodNotSupportedException`, `HttpMediaTypeNotSupportedException`, etc.), or
- Extend `ResponseEntityExceptionHandler` and override only the methods you need (see [Section 5](#5-upgrading-to-problemdetail-rfc-9457) for when and how to do that).

---

## 4. The ErrorResponse DTO

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp,
        Map<String, String> errors
) {
    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(
            status.value(),
            status.getReasonPhrase(),
            message,
            path,
            LocalDateTime.now(),
            null
        );
    }
}
```

### Field Rationale

| Field | Purpose |
|---|---|
| `status` | The numeric HTTP status code — useful for clients that log only the body, not the response line |
| `error` | The standard reason phrase (`"Not Found"`, `"Bad Request"`) — human-readable category |
| `message` | A plain-language description of this specific occurrence |
| `path` | The request URI — helps when correlating client-side error reports with server logs |
| `timestamp` | When the error occurred — useful for support and time-correlation |
| `errors` | Field-level validation errors (only present on 400 validation failures) |

### `@JsonInclude(NON_NULL)`

The `errors` field is `null` for every non-validation error. Without `@JsonInclude(NON_NULL)`, Jackson would serialise it as `"errors": null`, adding noise to every error response. The annotation suppresses any `null` field from the output entirely.

### Static Factory Method

`ErrorResponse.of(...)` is a convenience factory for the common case where there are no field-level errors. The handler calls it for everything except validation failures, keeping the handler code concise.

### Java Record vs Plain Class

Using a `record` makes `ErrorResponse` immutable and gives you `equals`, `hashCode`, and `toString` for free. The only constraint is that records cannot extend other classes — not a limitation here. If you need inheritance (for example, a subtype that adds security-specific fields), switch to a regular class with `@Getter` and `@Builder` from Lombok.

---

## 5. Upgrading to ProblemDetail (RFC 9457)

RFC 9457 (superseding RFC 7807) defines a standard JSON body format for HTTP error responses. Using it means clients — including third-party integrations — can parse your errors without reading your API documentation first.

### Standard ProblemDetail Shape

```json
{
  "type": "https://api.example.com/errors/resource-not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "User with id '99' was not found",
  "instance": "/api/users/99"
}
```

### How the Custom DTO Approach Compares

| Aspect | Custom `ErrorResponse` DTO | `ProblemDetail` (RFC 9457) |
|---|---|---|
| Setup | No special config needed | `spring.mvc.problemdetails.enabled=true` |
| Standardisation | Your own format — clients must read your docs | Industry-standard — clients may already know the shape |
| Flexibility | Full control over every field name | Extensible via `setProperty()`, core fields are fixed |
| Spring MVC errors | Fall through to `/error` unless explicitly handled | Inherited from `ResponseEntityExceptionHandler` |
| `Content-Type` | `application/json` | `application/problem+json` |
| Tooling support | Generic JSON tooling | RFC-aware tooling (some API gateways, monitoring tools) |

### Migrating from This Branch to ProblemDetail

**Step 1 — Enable ProblemDetail in Spring Boot:**

```properties
spring.mvc.problemdetails.enabled=true
```

**Step 2 — Extend `ResponseEntityExceptionHandler`:**

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    // ...
}
```

**Step 3 — Replace `ErrorResponse.of(...)` with `ProblemDetail`:**

```java
@ExceptionHandler(ResourceNotFoundException.class)
public ResponseEntity<ProblemDetail> handleNotFound(
        ResourceNotFoundException ex, HttpServletRequest request) {

    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    problem.setInstance(URI.create(request.getRequestURI()));
    // Add custom fields:
    problem.setProperty("errorCode", "RESOURCE_NOT_FOUND");
    problem.setProperty("traceId", UUID.randomUUID().toString());
    problem.setProperty("timestamp", Instant.now());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
}
```

**Step 4 — Override inherited handlers** for Spring MVC internals you want to customise (e.g. `handleMethodArgumentNotValid`).

### When to Choose ProblemDetail Over a Custom DTO

- You are building a public or third-party-facing API.
- Your clients may already be tooled to parse the RFC 9457 format.
- You want Spring Boot to handle framework-level errors (405, 415, etc.) in the same format automatically.
- You need a stable, linkable `type` URI that points to your error documentation.

---

## 6. Other Alternative Patterns

### Pattern 1: `@ResponseStatus` on the Exception Class

The simplest approach. Annotate the exception class directly.

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
```

**When to use:** Prototyping, very small applications, or when you just need the correct status code and do not care about the response body format.

**Limitations:**
- No control over the response body.
- The annotation is silently ignored when a `@ControllerAdvice` with a matching `@ExceptionHandler` is present — the advice intercepts the exception first. This is the most common source of confusion with `@ResponseStatus`.
- Cannot include machine-readable error codes or structured field errors.

---

### Pattern 2: Per-Controller `@ExceptionHandler`

Handle exceptions directly inside the controller that throws them.

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<String> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }
}
```

**When to use:** When one controller has unique error handling requirements that differ from the rest of the application.

**Limitations:**
- Each controller must duplicate its own handlers — leads to inconsistency as the codebase grows.
- Does not handle Spring MVC internal exceptions.

---

### Pattern 3: `@ControllerAdvice` Extending `ResponseEntityExceptionHandler`

Extends Spring's built-in handler so that framework-level exceptions (405, 415, 400 from malformed JSON, etc.) are covered by the same advice class.

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        // Override to use your own response format
    }
}
```

**When to use:** When you need every possible exception — including Spring MVC internals — to return a consistent response shape. This is the natural upgrade path from this branch.

**Trade-off:** Slightly more complex; the return type of overridden methods is `ResponseEntity<Object>` rather than `ResponseEntity<ErrorResponse>`, which requires a cast or a common base type.

---

### Pattern 4: `ErrorAttributes` Bean

Customise the `/error` endpoint that Spring Boot's `BasicErrorController` uses — the fallback for errors that never reached any `@ExceptionHandler`.

```java
@Component
public class CustomErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
        Map<String, Object> attrs = super.getErrorAttributes(request, options);
        attrs.put("errorCode", "UNHANDLED_ERROR");
        attrs.remove("trace");
        return attrs;
    }
}
```

**When to use:** As a safety net for errors thrown from filters or during view rendering, where `@ControllerAdvice` never runs. Works well alongside a `@ControllerAdvice` to keep the `/error` fallback consistent with your main error format.

---

### Pattern 5: `HandlerExceptionResolver`

The lowest-level extension point — rarely needed in application code.

```java
@Component
public class CustomExceptionResolver implements HandlerExceptionResolver, Ordered {

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        if (ex instanceof ResourceNotFoundException nfe) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return new ModelAndView();
        }
        return null;
    }
}
```

**When to use:** When writing a framework or library that must intercept exceptions before the application's own handlers. Application developers almost never need this.

---

### Spring Security Integration

When Spring Security is on the classpath, authorization failures throw `AccessDeniedException` (403) and authentication failures throw `AuthenticationException` (401). These are handled by Security's `ExceptionTranslationFilter` before they reach your `@ControllerAdvice`.

To produce consistent `ErrorResponse` bodies for security errors, configure custom entry points:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.exceptionHandling(ex -> ex
        .authenticationEntryPoint((request, response, authException) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            // write ErrorResponse JSON manually or via ObjectMapper
        })
        .accessDeniedHandler((request, response, accessDeniedException) -> {
            response.setStatus(403);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        })
    );
    return http.build();
}
```

---

## 7. When to Use What

| Scenario | Recommended Pattern |
|---|---|
| Learning / first Spring Boot project | **Custom `ErrorResponse` DTO** + plain `@ControllerAdvice` (this branch) |
| Small prototype, just need correct status code | `@ResponseStatus` on exception class |
| One controller with unique error format needs | Per-controller `@ExceptionHandler` |
| Consistent JSON errors, internal API | Custom `ErrorResponse` DTO + `@ControllerAdvice` |
| Consistent JSON errors, public/third-party API | `@ControllerAdvice` + `ProblemDetail` (RFC 9457) |
| Need framework errors (405, 415) in same format | Extend `ResponseEntityExceptionHandler` |
| Errors from filters need consistent format | `ErrorAttributes` bean as a complement |
| Writing a framework/library | `HandlerExceptionResolver` |
| Spring Security 401/403 consistent format | Custom `AuthenticationEntryPoint` + `AccessDeniedHandler` |

### Decision Flowchart

```
Do you need a consistent response format across all endpoints?
├── No  → @ResponseStatus or per-controller @ExceptionHandler
└── Yes → @ControllerAdvice
          │
          Do you need RFC 9457 compliance or are you building a public API?
          ├── No  → Custom ErrorResponse DTO (simpler, full control) ← this branch
          └── Yes → ProblemDetail
                    │
                    Do you need framework errors (405, 415, etc.) in the same format?
                    ├── No  → Plain @ControllerAdvice with ProblemDetail
                    └── Yes → Extend ResponseEntityExceptionHandler
```

---

## 8. Common Pitfalls

### `@ResponseStatus` Is Silently Ignored When `@ControllerAdvice` Is Present

This is the most common point of confusion. If you annotate an exception with `@ResponseStatus(HttpStatus.NOT_FOUND)` and also have a `@ControllerAdvice` with a matching `@ExceptionHandler`, the annotation is **never applied**. The advice intercepts the exception first.

`@ResponseStatus` only takes effect when the exception propagates all the way to the servlet layer without being caught by any handler. Once a `@ControllerAdvice` is in play, that almost never happens for application exceptions.

---

### Spring MVC Exceptions Use a Different Response Shape by Default

Because this handler does not extend `ResponseEntityExceptionHandler`, a request with an unsupported HTTP method returns Spring Boot's default `Whitelabel Error Page` or its default JSON error body — not your `ErrorResponse`. If a client calls `DELETE /api/products/1` and `DELETE` is not mapped, they get a response that looks completely different from all other errors.

If consistent responses for those errors matter, add explicit handlers or extend `ResponseEntityExceptionHandler`.

---

### Missing Field in Some Handlers Breaks Client Contracts

If you add a new field to `ErrorResponse` (for example, `errorCode`) but forget to populate it in one handler, clients that key on that field will fail silently for that one exception type. When you change your error contract, audit every handler — including the catch-all — to ensure the shape is consistent in all cases.

---

### The Catch-All Must Never Expose `ex.getMessage()`

```java
// NEVER do this in a catch-all:
ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request.getRequestURI())
```

A `NullPointerException`, SQL exception, or deserialization error can have a message that includes table names, column names, file paths, or class names. These are security-sensitive details. The catch-all handler must return a fixed, safe message and log the real one server-side.

---

### `cancelOrder` Must Restore Product Stock

When an order is cancelled, the stock reduction from when the order was placed must be reversed. Forgetting this means stock is permanently lost on every cancellation — a silent data integrity bug that compounds over time.

The fix belongs inside the same transaction as the status update so that both changes either commit or roll back together.

---

### `@Transactional(readOnly = true)` on a Method Called Within a Write Transaction

When a `readOnly = true` method is called from within an existing read-write `@Transactional` method, Spring's default propagation (`REQUIRED`) causes the inner method to join the outer transaction. The `readOnly` hint on the inner method is ignored — the outer transaction's setting wins.

This does not cause a bug, but it can mislead readers into thinking a method is safe to call without a surrounding transaction when it actually depends on one.

---

### Lazy Association Loading After the Session Closes

JPA associations marked `FetchType.LAZY` are only loaded while the persistence context is open. If you map an entity to a DTO after the transaction has committed, accessing `order.getUser().getName()` throws `LazyInitializationException`.

The fix: always map entities to DTOs inside the `@Transactional` method, before the session closes. Setting `spring.jpa.open-in-view=false` (as this project does) is an explicit opt-out of the anti-pattern that keeps the session open for the entire HTTP request lifetime.
