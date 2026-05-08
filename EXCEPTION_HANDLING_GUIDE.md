# Spring Boot Exception Handling — Architecture & Patterns Guide

This guide walks through the exception handling architecture used in this project, explains every design decision, and surveys the alternative patterns available in Spring Boot — including the simpler approaches that do not use `ProblemDetail` at all — so you can make an informed choice in your own applications.

> **A note on scope:** Most tutorials you will find online teach a custom `ErrorResponse` DTO approach. That approach works well and is widely used in production. This project goes one step further by adopting the RFC 9457 standard, which many modern APIs are converging on. Read [Section 5](#5-exception-handling-without-problemdetail) to understand the simpler approach first — then the architecture in this project will make more sense by comparison.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [The Exception Hierarchy](#2-the-exception-hierarchy)
3. [The Global Handler Deep Dive](#3-the-global-handler-deep-dive)
4. [RFC 9457 — Problem Details for HTTP APIs](#4-rfc-9457--problem-details-for-http-apis)
5. [Exception Handling Without ProblemDetail](#5-exception-handling-without-problemdetail)
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
         │ throws AppException subtype
         ▼
┌────────────────────────────────┐
│     GlobalExceptionHandler     │  @RestControllerAdvice
│                                │  Extends ResponseEntityExceptionHandler
│  @ExceptionHandler(...)        │
│  handleAppException()          │
│  handleMethodArgNotValid()     │
│  handleDataIntegrityViolation()│
│  handleGenericException()      │
└────────────────────────────────┘
         │ builds
         ▼
┌────────────────────┐
│    ProblemDetail   │  RFC 9457-compliant JSON body
│  {                 │
│    "type": "...",  │
│    "title": "...", │
│    "status": 404,  │
│    "detail": "...",│
│    "errorCode":".",│
│    "traceId": ".", │
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
| **Exception classes** | Carry the HTTP status and machine-readable error code; no handler logic |
| **GlobalExceptionHandler** | Single place that converts any exception into a consistent RFC 9457 response |
| **ErrorCode** | Registry of stable, machine-readable string codes for observability and client-side routing |

The key principle: **exceptions carry data, the handler decides the response.** No business logic lives in the handler; no HTTP concerns live in the service.

---

## 2. The Exception Hierarchy

### The Base Class: `AppException`

```
RuntimeException
     └── AppException          (abstract — carries status + errorCode)
              ├── ResourceNotFoundException   (404)
              ├── ResourceGoneException       (410)
              ├── ConflictException           (409)
              ├── BusinessRuleException       (422)
              ├── ForbiddenException          (403)
              └── ExternalServiceException    (502)
```

`AppException` is abstract and holds two fields: `HttpStatus status` and `String errorCode`. Every subclass sets these in its constructor. The global handler reads them directly — it never switches on the concrete type.

```java
// The handler is fully decoupled from subtypes.
// Adding a new exception class requires zero changes here.
@ExceptionHandler(AppException.class)
public ResponseEntity<ProblemDetail> handleAppException(AppException ex, HttpServletRequest request) {
    ProblemDetail problem = buildProblemDetail(ex.getStatus(), ex.getMessage(), ex.getErrorCode(), request);
    return ResponseEntity.status(ex.getStatus()).body(problem);
}
```

This is the **Open/Closed Principle** applied to exception handling: the handler is open for extension (new subtypes) and closed for modification.

### Why `RuntimeException` and Not `Exception`?

Checked exceptions (`Exception`) force every calling method to declare `throws` or catch the exception. In a layered Spring application that adds enormous noise — services, repositories, and controllers would all need try/catch or `throws` declarations just to let the exception reach the global handler. `RuntimeException` propagates freely up the call stack without that ceremony.

### The Two-Constructor Pattern

`AppException` provides two constructors: one with a cause, one without.

```java
protected AppException(String message, String errorCode, HttpStatus status) {
    super(message);
    ...
}

protected AppException(String message, String errorCode, HttpStatus status, Throwable cause) {
    super(message, cause);
    ...
}
```

The `cause` constructor enforces **exception chaining** — wrapping a lower-level exception without losing its original stack trace. `ExternalServiceException` always requires a cause because its purpose is to wrap infrastructure failures:

```java
// In the controller, the original RuntimeException is never swallowed.
try {
    simulateExternalPricingCall(product.sku());
} catch (RuntimeException e) {
    throw new ExternalServiceException("PricingService", "Failed to retrieve live pricing data", e);
}
```

Without chaining, the log would only show `ExternalServiceException`. With it, the full chain — including the original timeout message — appears, making incidents significantly easier to diagnose.

### The ErrorCode Registry

```java
public final class ErrorCode {
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String USER_EMAIL_CONFLICT = "USER_EMAIL_CONFLICT";
    // ...
    private ErrorCode() {}
}
```

`ErrorCode` is a non-instantiable utility class (private constructor) that acts as a central registry of all machine-readable error codes. Every error response includes one of these codes in the `errorCode` field.

**Why string constants and not an enum?**

Both are valid choices. The trade-offs:

| | String Constants | Enum |
|---|---|---|
| Compiler safety | ✗ Any string can be passed | ✓ Only valid values compile |
| Exhaustive switch | ✗ Not enforced | ✓ Compiler warns on missing cases |
| JSON serialization | Simple | Requires `@JsonValue` or custom serializer |
| Adding new codes | Add a constant | Add an enum value |
| IDE navigation | ✓ Find usages works | ✓ Find usages works |

String constants are chosen here to keep the code simpler and more readable for a teaching context. In a production codebase shared by multiple teams, an enum is often the better choice because the compiler catches typos.

---

## 3. The Global Handler Deep Dive

### Why Extend `ResponseEntityExceptionHandler`?

Spring MVC throws its own internal exceptions for common failure cases — malformed JSON, unsupported HTTP methods, missing required headers, and so on. If you write a plain `@ControllerAdvice` from scratch, none of those are handled by your code; Spring falls back to its own default format (which differs from your custom format).

`ResponseEntityExceptionHandler` already handles all of them. By extending it, your advice inherits those handlers and can override just the ones you need to reformat — as is done here for `MethodArgumentNotValidException`:

```java
@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {

    // Override to add field-level error map and keep the RFC 9457 shape.
    Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
        .collect(Collectors.toMap(
            FieldError::getField,
            fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
            (first, second) -> first + "; " + second
        ));
    ...
}
```

### Handler Resolution Priority

When an exception is thrown, Spring evaluates `@ExceptionHandler` methods in this order:

1. **Most specific type first** — `ResourceNotFoundException` matches `AppException.class` but also `RuntimeException.class`. Spring picks the most specific matching handler.
2. **Declared in the same class first** — handlers in your `@ControllerAdvice` take priority over inherited ones from `ResponseEntityExceptionHandler`.
3. **`@ExceptionHandler(Exception.class)` last** — the catch-all. Never place this handler before more specific ones.

```
Exception thrown
      │
      ├── Is it an AppException?         → handleAppException()
      ├── Is it a ResponseStatusException? → handleResponseStatusException()
      ├── Is it a DataIntegrityViolation? → handleDataIntegrityViolation()
      ├── Is it a Spring MVC internal?   → inherited from ResponseEntityExceptionHandler
      └── Anything else                  → handleGenericException() [catch-all 500]
```

### The `traceId` Field and MDC

Every response includes a `traceId`. The handler first checks `MDC.get("traceId")` — MDC (Mapped Diagnostic Context) is a thread-local map populated by tracing libraries (e.g. Spring Cloud Sleuth, Micrometer Tracing, or a custom servlet filter). If a trace ID is already set, it's reused; otherwise a fresh UUID is generated.

```java
private String resolveTraceId() {
    String traceId = MDC.get("traceId");
    return traceId != null ? traceId : UUID.randomUUID().toString();
}
```

This means: in a system with distributed tracing, every error response automatically carries the same trace ID that appears in your logs and spans — making it trivial to find the full request context for any error a client reports.

### The Catch-All Handler

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception at {}", request.getRequestURI(), ex);

    ProblemDetail problem = buildProblemDetail(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "An unexpected error occurred. Please try again later.",
        ErrorCode.INTERNAL_ERROR,
        request
    );

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
}
```

Two things are intentional here:

- **Full stack trace is logged** (`log.error(..., ex)` passes the exception as the last argument, which SLF4J renders as a full stack trace).
- **No internal detail is exposed to the client.** The message is a generic safe string. Never return `ex.getMessage()` from a catch-all — it can leak class names, SQL queries, or file paths.

---

## 4. RFC 9457 — Problem Details for HTTP APIs

RFC 9457 (superseding RFC 7807) defines a standard JSON body format for HTTP error responses. Using it means clients — including third-party integrations — can parse your errors without reading your API documentation first.

### Standard Fields

```json
{
  "type": "https://api.javaguy.com/errors/resource-not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "User with id '99' was not found",
  "instance": "/api/users/99"
}
```

| Field | Meaning |
|---|---|
| `type` | A URI that identifies the error category. Should be stable across versions. |
| `title` | Short human-readable summary. Should not change per-occurrence. |
| `status` | The HTTP status code (redundant with the response status, but useful for clients that log body only). |
| `detail` | Human-readable explanation specific to this occurrence. |
| `instance` | URI of the specific request that caused the error. |

### Extension Fields (RFC 9457 §3.2)

RFC 9457 explicitly allows extending the body with additional fields. This project adds three:

```json
{
  "errorCode": "RESOURCE_NOT_FOUND",
  "traceId":   "b3a2f1c0-...",
  "timestamp": "2026-05-08T10:15:30.123Z"
}
```

- `errorCode` — machine-readable, stable code for alert routing and client error handling
- `traceId` — links this error to a distributed trace or log entry
- `timestamp` — ISO-8601 instant for client-side diagnostics

### Enabling Spring Boot's Built-in ProblemDetail Support

```properties
spring.mvc.problemdetails.enabled=true
```

This single property instructs Spring Boot to use `ProblemDetail` as the response body for all exceptions handled by `ResponseEntityExceptionHandler`, including the built-in handlers you did not override. Without it, those inherited handlers return their own format.

---

## 5. Exception Handling Without ProblemDetail

Before RFC 9457 became widely adopted, the most common approach was a **custom `ErrorResponse` DTO**. You will see this pattern in the majority of Spring Boot tutorials and many production codebases. It is simpler to set up and requires no special Spring Boot configuration.

### The Custom ErrorResponse DTO Pattern

**Step 1 — Define a response record (or class) for your error body:**

```java
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp
) {}
```

**Step 2 — Create custom exception classes:**

```java
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
```

**Step 3 — Write a global handler that returns your DTO:**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {

        ErrorResponse body = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            "Not Found",
            ex.getMessage(),
            request.getRequestURI(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));

        ErrorResponse body = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Validation Failed",
            message,
            request.getRequestURI(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAll(
            Exception ex, HttpServletRequest request) {

        ErrorResponse body = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "An unexpected error occurred.",
            request.getRequestURI(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
```

**What the response looks like:**

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "User with id '99' was not found",
  "path": "/api/users/99",
  "timestamp": "2026-05-08T10:15:30.123"
}
```

### How This Compares to the ProblemDetail Approach

| Aspect | Custom ErrorResponse DTO | ProblemDetail (RFC 9457) |
|---|---|---|
| Setup | No special config needed | `spring.mvc.problemdetails.enabled=true` |
| Standardisation | Your own format — clients must read your docs | Industry-standard — clients may already know the shape |
| Flexibility | Full control over every field | Extensible via `setProperty()`, core fields are fixed |
| Spring MVC errors | Must handle each one explicitly | Inherited from `ResponseEntityExceptionHandler` |
| `Content-Type` | `application/json` | `application/problem+json` |
| Tooling support | Generic JSON tooling | RFC-aware tooling (some API gateways, monitoring tools) |

### When to Choose the Custom DTO Approach

- You are building an internal API where you control all consumers and a shared contract is easy to maintain.
- Your team is more comfortable with explicit, home-grown code than with framework abstractions.
- You need a response shape that does not fit the RFC 9457 structure at all.
- You are learning — the custom DTO approach has fewer moving parts and is easier to trace from request to response.

### When to Choose ProblemDetail (RFC 9457)

- You are building a public or third-party-facing API.
- Your clients may already be tooled to parse the RFC 9457 format.
- You want Spring Boot to handle framework-level errors (405, 415, etc.) in the same format automatically.
- You need the `type` URI to serve as a stable, linkable reference to your error documentation.

> **Bottom line:** Both approaches are valid and production-ready. The custom DTO gives you simplicity and full control. ProblemDetail gives you standardisation and free handling of Spring MVC internals. This project uses ProblemDetail as a teaching example of the modern approach — but if you are starting out, there is nothing wrong with beginning with a custom DTO and migrating later.

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

**What happens:** When this exception reaches the servlet layer unhandled, Spring's `DefaultHandlerExceptionResolver` reads the annotation and sets the response status. The response body is Spring's default error format (not RFC 9457).

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

**When to use:** When one controller has unique error handling requirements that differ from the rest of the application. For example, a file upload controller that returns a plaintext error message while the rest of the API uses JSON.

**Limitations:**
- Does not apply to other controllers — each controller must duplicate its own handlers.
- Leads to inconsistent error responses across the API as the codebase grows.
- Does not handle Spring MVC internal exceptions.

---

### Pattern 3: `@ControllerAdvice` Without Extending `ResponseEntityExceptionHandler`

A global handler written from scratch, without inheriting Spring's built-in handling.

```java
@RestControllerAdvice
public class SimpleGlobalHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAll(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "Something went wrong"));
    }
}
```

**When to use:** When you want a global handler but do not need or want Spring's built-in exception handling for framework errors. Simpler to reason about; zero inherited behaviour.

**Limitations:**
- Spring MVC internal exceptions (method not allowed, unsupported media type, etc.) fall through to Spring's default error handling and will have a different response format from your custom handlers — unless you also handle them explicitly.
- You must re-implement everything `ResponseEntityExceptionHandler` gives you for free if you need consistent responses for all error types.

---

### Pattern 4: `ErrorAttributes` Bean

Customise the `/error` endpoint that Spring Boot's `BasicErrorController` uses — the fallback that handles errors which never reached any `@ExceptionHandler`.

```java
@Component
public class CustomErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
        Map<String, Object> attrs = super.getErrorAttributes(request, options);
        attrs.put("errorCode", "UNHANDLED_ERROR");
        attrs.put("timestamp", Instant.now());
        // Remove fields you do not want exposed
        attrs.remove("trace");
        return attrs;
    }
}
```

**When to use:** When you need a safety net for errors that bypass all `@ExceptionHandler` methods — for example, errors thrown from filters or during view rendering. Works well alongside a `@ControllerAdvice` to ensure the `/error` fallback is consistent with your main error format.

**Limitations:**
- Only applies to requests routed through `/error`. Most application exceptions are caught before reaching that endpoint.
- Does not replace `@ControllerAdvice`; it complements it.

---

### Pattern 5: `HandlerExceptionResolver`

The lowest-level extension point. Implement `HandlerExceptionResolver` to intercept exceptions before Spring's resolver chain processes them.

```java
@Component
public class CustomExceptionResolver implements HandlerExceptionResolver, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        if (ex instanceof ResourceNotFoundException nfe) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            // write response manually
            return new ModelAndView();
        }
        return null; // let the next resolver handle it
    }
}
```

**When to use:** Rarely. This is appropriate only when you need exception handling logic to run before Spring MVC's resolver chain — for example, in a framework or library that must not interfere with the application's own handlers. Application developers almost never need this.

**Limitations:**
- Requires writing raw `HttpServletResponse` output manually.
- Complex to get right; easy to break content negotiation.

---

### Spring Security Integration

When Spring Security is on the classpath, authorization failures throw `AccessDeniedException` (403) and authentication failures throw `AuthenticationException` (401). These are handled by Security's own `ExceptionTranslationFilter` before they reach your `@ControllerAdvice`.

To produce consistent RFC 9457 bodies for security errors, configure custom entry points:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.exceptionHandling(ex -> ex
        .authenticationEntryPoint((request, response, authException) -> {
            // write 401 ProblemDetail manually
        })
        .accessDeniedHandler((request, response, accessDeniedException) -> {
            // write 403 ProblemDetail manually
        })
    );
    return http.build();
}
```

Alternatively, configure Security to delegate 401/403 to your `@ControllerAdvice` via `HttpStatusEntryPoint` and a custom `AccessDeniedHandler` that rethrows as `ForbiddenException`.

---

## 7. When to Use What

| Scenario | Recommended Pattern |
|---|---|
| Learning / first Spring Boot project | Custom `ErrorResponse` DTO + plain `@ControllerAdvice` |
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
          Do you need RFC 9457 compliance?
          ├── No  → Custom ErrorResponse DTO (simpler, full control)
          └── Yes → ProblemDetail
                    │
                    Do you need framework errors (405, 415, etc.) in the same format?
                    ├── No  → Plain @ControllerAdvice with ProblemDetail
                    └── Yes → Extend ResponseEntityExceptionHandler (this project)
```

---

## 8. Common Pitfalls

### `@ResponseStatus` Is Silently Ignored When `@ControllerAdvice` Is Present

This is the most common point of confusion. If you annotate an exception with `@ResponseStatus(HttpStatus.NOT_FOUND)` and also have a `@ControllerAdvice` with `@ExceptionHandler(AppException.class)`, the annotation is **never applied**. The advice intercepts the exception first.

`@ResponseStatus` only takes effect when the exception propagates all the way to the servlet layer without being caught by any handler. Once a `@ControllerAdvice` is in play, that almost never happens for application exceptions.

This project retains `@ResponseStatus` on `ResourceNotFoundException` and `ResourceGoneException` as **self-documentation** — it communicates intent clearly — and as a safety net if the global handler were ever removed. But the annotation has no runtime effect here.

---

### Missing `errorCode` in Some Handlers Breaks Client Contracts

If all handlers except one set an `errorCode` in the response body, any client that keys on `errorCode` will throw a `NullPointerException` or fail silently on the one exception type that does not include it. When you add a field to your error contract, it must be present in every response — including inherited handlers from `ResponseEntityExceptionHandler` that you did not override.

Always audit every code path through your handler to verify the response shape is consistent.

---

### The Catch-All Must Never Expose `ex.getMessage()`

```java
// NEVER do this in a catch-all:
.body(Map.of("error", ex.getMessage()))
```

A `NullPointerException`, SQL exception, or deserialization error can have a message that includes table names, column names, file paths, or class names. These are security-sensitive details. The catch-all handler must return a fixed, safe message and log the real one server-side.

---

### `cancelOrder` Must Restore Product Stock

When an order is cancelled, the stock reduction from when the order was placed must be reversed. Forgetting this means stock is permanently lost on every cancellation — a silent data integrity bug that compounds over time and can only be caught by auditing orders against inventory.

The fix belongs inside the same transaction as the status update so that both changes either commit or roll back together.

---

### `@Transactional(readOnly = true)` on a Method Called Within a Write Transaction

When a `readOnly = true` method is called from within an existing read-write `@Transactional` method, Spring's default propagation (`REQUIRED`) causes the inner method to **join the outer transaction**. The `readOnly` hint on the inner method is ignored — the outer transaction's setting wins.

This does not cause a bug, but it can mislead readers into thinking a method is safe to call without a surrounding transaction when it actually depends on one. Document these dependencies clearly.

---

### Lazy Association Loading After the Session Closes

JPA associations marked `FetchType.LAZY` are only loaded while the persistence context (session) is open. If you pass an entity to a mapping method (`OrderResponse.from(order)`) after the transaction has committed and the session has closed, accessing `order.getUser().getName()` throws `LazyInitializationException`.

The fix: always map entities to DTOs **inside** the `@Transactional` method, before the session closes. This project does that correctly — `OrderResponse.from(order)` is always called within an active transaction. Setting `spring.jpa.open-in-view=false` (as this project does) is an explicit opt-out of the anti-pattern that keeps the session open for the entire HTTP request lifetime, forcing you to think about this properly.
