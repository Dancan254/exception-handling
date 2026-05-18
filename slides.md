---
marp: true
theme: default
paginate: true
style: |
  @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&family=JetBrains+Mono:wght@400;600&display=swap');

  section {
    font-family: 'Inter', 'Segoe UI', sans-serif;
    background: #ffffff;
    color: #1a1a1a;
    padding: 60px 80px;
  }

  /* Accent bar at top of every slide */
  section::before {
    content: '';
    position: absolute;
    top: 0; left: 0; right: 0;
    height: 4px;
    background: #7B2FFF;
  }

  /* Page number */
  section::after {
    font-family: 'JetBrains Mono', monospace;
    color: #aaa;
    font-size: 0.7rem;
  }

  h1 {
    font-size: 2.2rem;
    font-weight: 700;
    color: #7B2FFF;
    margin-bottom: 0.2em;
    letter-spacing: -0.02em;
  }

  h2 {
    font-size: 1.6rem;
    font-weight: 700;
    color: #1a1a1a;
    border-left: 4px solid #7B2FFF;
    padding-left: 16px;
    margin-bottom: 0.6em;
  }

  h3 {
    font-size: 0.95rem;
    font-weight: 400;
    color: #888;
    letter-spacing: 0.02em;
  }

  code {
    font-family: 'JetBrains Mono', monospace;
    background: #f0ebff;
    color: #5b10cc;
    padding: 2px 7px;
    border-radius: 4px;
    font-size: 0.88em;
  }

  pre {
    font-family: 'JetBrains Mono', monospace;
    background: #f8f8f8;
    border: 1px solid #e2e2e2;
    border-left: 3px solid #7B2FFF;
    border-radius: 6px;
    padding: 16px 20px;
    font-size: 0.72rem;
    line-height: 1.55;
    overflow: hidden;
  }

  pre code {
    background: none;
    color: #1a1a1a;
    padding: 0;
    font-size: inherit;
  }

  strong { color: #7B2FFF; }
  em     { color: #d97706; font-style: normal; }

  li { margin-bottom: 0.5em; line-height: 1.5; }

  table {
    border-collapse: collapse;
    width: 100%;
    font-size: 0.85rem;
  }
  th {
    background: #f0ebff;
    color: #5b10cc;
    padding: 10px 14px;
    text-align: left;
    border-bottom: 2px solid #7B2FFF;
  }
  td {
    padding: 8px 14px;
    border-bottom: 1px solid #e8e8e8;
    color: #333;
  }

  /* Title slide override */
  section.title {
    display: flex;
    flex-direction: column;
    justify-content: center;
    background: #f5f0ff;
  }
  section.title::before { height: 6px; }
  section.title h1 { font-size: 3rem; color: #7B2FFF; }
  section.title h3 { font-size: 1.1rem; color: #888; margin-top: 0.5em; }

  /* Closing slide override */
  section.closing {
    display: flex;
    flex-direction: column;
    justify-content: center;
    background: #f5f0ff;
    text-align: center;
  }
  section.closing h1 { font-size: 2.8rem; text-align: center; }
  section.closing blockquote {
    border-left: none;
    text-align: center;
    color: #888;
    font-size: 0.95rem;
    margin-top: 2em;
  }
---

<!-- _paginate: false -->
<!-- _class: title -->

# Exception Handling in Spring Boot
### From basics to RFC 9457

---

## What is an exception?

A signal that the normal flow cannot continue.
Thrown at **runtime**, but checked exceptions are enforced at **compile time**.

```java
// Normal flow cannot proceed — signal to the caller
throw new RuntimeException("User not found");
```

Java uses **exceptions as objects** — they carry context, have a type, and can be caught selectively.

---

## Java's exception hierarchy

```
Throwable
├── Error            ← JVM-level, don't catch
└── Exception
    ├── IOException  ← Checked: must declare or catch
    └── RuntimeException  ← Unchecked: no obligation to catch
        ├── NullPointerException
        ├── IllegalArgumentException
        └── ... your custom exceptions
```

In Spring apps: **always use `RuntimeException`**. Checked exceptions force callers to handle what they often can't.

---

## The anti-patterns

```java
// ❌ Return null — caller forgets to check
User user = userRepository.findById(id);
return user; // null if not found

// ❌ Return a magic value
return -1; // what does -1 mean?

// ❌ Catch and swallow
try { ... } catch (Exception e) { /* nothing */ }

// ❌ Leak internal details
throw new RuntimeException(ex.getSQLException().getMessage());
```

Exceptions should be **intentional, typed, and informative**.

---

## Before RFC 9457 — the chaos

Every team invented their own error shape:

```json
{ "error": "not found" }
{ "message": "User 99 does not exist", "code": 404 }
{ "errorCode": "USR_404", "description": "..." }
```

**Problems:**
- Clients can't write generic error handling
- No standard for machines or humans
- HTTP status alone is too coarse (`404` could mean 10 different things)

---

## RFC 9457 — Problem Details for HTTP APIs

A *standard* error response format. Every field has a defined purpose.

```json
{
  "type":     "https://api.example.com/errors/user-not-found",
  "title":    "Not Found",
  "status":   404,
  "detail":   "User with id '99' was not found.",
  "instance": "/api/users/99"
}
```

| Field | Purpose |
|---|---|
| `type` | URI identifying this *class* of error (stable, linkable) |
| `title` | Human summary — same for all instances of this type |
| `detail` | This specific occurrence |
| `instance` | The exact request URI that triggered it |

---

## Spring's native support

Spring 6+ ships `ProblemDetail` out of the box.

```properties
# application.properties — one line to enable
spring.mvc.problemdetails.enabled=true
```

```java
// Spring's ProblemDetail class
ProblemDetail problem = ProblemDetail
    .forStatusAndDetail(HttpStatus.NOT_FOUND, "User not found");
problem.setType(URI.create("https://api.example.com/errors/not-found"));
problem.setTitle("Not Found");
problem.setInstance(URI.create("/api/users/99"));
```

No third-party library. No custom serializer. **Just use it.**

---

## Extension properties (§3.2)

RFC 9457 allows custom fields — this is where you add **observability**.

```json
{
  "type":      "https://api.javaguy.com/errors/resource-not-found",
  "title":     "Not Found",
  "status":    404,
  "detail":    "User with id '99' was not found.",
  "instance":  "/api/users/99",

  "errorCode": "RESOURCE_NOT_FOUND",
  "traceId":   "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-05-18T14:32:00.123456Z"
}
```

`errorCode` → machine-readable alert routing key
`traceId` → correlate this request across logs and services
`timestamp` → client-side diagnostics

---

## Build a custom exception hierarchy

```java
public abstract class AppException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    // status + code live on the exception — handler stays generic
    protected AppException(String message, String errorCode, HttpStatus status) { ... }
}
```

```java
public class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String resource, String field, Object value) {
        super(
            "%s with %s '%s' was not found".formatted(resource, field, value),
            ErrorCode.RESOURCE_NOT_FOUND,
            HttpStatus.NOT_FOUND
        );
    }
}
```

One exception type per **semantic scenario**, not per HTTP status.

---

## Machine-readable error codes

```java
public final class ErrorCode {
    // Generic
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String DATA_CONFLICT      = "DATA_CONFLICT";
    public static final String VALIDATION_FAILED  = "VALIDATION_FAILED";
    public static final String INTERNAL_ERROR     = "INTERNAL_ERROR";

    // Domain-specific
    public static final String USER_EMAIL_CONFLICT   = "USER_EMAIL_CONFLICT";
    public static final String INSUFFICIENT_STOCK    = "PRODUCT_INSUFFICIENT_STOCK";
    public static final String ORDER_INVALID_CANCEL  = "ORDER_INVALID_CANCEL";
}
```

Pattern: `DOMAIN_CONDITION` — stable strings your dashboard can key on.

---

## The global handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ProblemDetail> handleAppException(
            AppException ex, HttpServletRequest request) {

        ProblemDetail problem = buildProblemDetail(
            ex.getStatus(), ex.getMessage(), ex.getErrorCode(), request
        );
        return ResponseEntity.status(ex.getStatus()).body(problem);
    }
}
```

`AppException` carries its own `status` and `errorCode` — **the handler never needs to know the subtype**. Adding a new exception requires zero changes here.

---

## Extending `ResponseEntityExceptionHandler`

```java
// ✅ Do this
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler { ... }

// ❌ Not this
public class GlobalExceptionHandler { ... }
```

`ResponseEntityExceptionHandler` already handles:
- `MethodArgumentNotValidException` (400)
- `HttpRequestMethodNotSupportedException` (405)
- `HttpMediaTypeNotSupportedException` (415)
- ...and a dozen more Spring MVC exceptions

You **override** them to produce your consistent `ProblemDetail` shape.

---

## Validation errors

Override `handleMethodArgumentNotValid` to add a field-level error map:

```java
@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex, HttpHeaders headers,
        HttpStatusCode status, WebRequest request) {

    Map<String, String> fieldErrors = ex.getBindingResult()
        .getFieldErrors().stream()
        .collect(Collectors.toMap(
            FieldError::getField,
            fe -> fe.getDefaultMessage(),
            (a, b) -> a + "; " + b
        ));

    ProblemDetail problem = buildProblemDetail(...);
    problem.setProperty("errors", fieldErrors);
    return ResponseEntity.status(status).headers(headers).body(problem);
}
```

---

## Validation response

```json
{
  "type":      "https://api.javaguy.com/errors/validation-failed",
  "title":     "Validation Failed",
  "status":    400,
  "detail":    "One or more request fields failed validation.",
  "instance":  "/api/users",
  "errorCode": "VALIDATION_FAILED",
  "traceId":   "...",
  "timestamp": "...",

  "errors": {
    "email":    "must be a well-formed email address",
    "quantity": "must be greater than 0; must be less than 1000"
  }
}
```

---

## Exception chaining

When wrapping a lower-level exception, **always pass the cause**.

```java
// ✅ Cause preserved — full stack trace in logs
try {
    simulateExternalPricingCall(product.sku());
} catch (RuntimeException e) {
    throw new ExternalServiceException("PricingService", "Failed to retrieve pricing", e);
}

// ❌ Cause swallowed — original stack trace gone
throw new ExternalServiceException("PricingService", "Failed to retrieve pricing");
```

The client sees a clean `502 Bad Gateway`. Logs contain the full chain.

---

## Handler resolution order

```
Request → Controller → Exception thrown
                              │
                    ┌─────────▼──────────┐
                    │  Most specific     │ AppException, ResponseStatusException
                    │  @ExceptionHandler │ DataIntegrityViolationException
                    └─────────┬──────────┘
                              │ (if no match)
                    ┌─────────▼──────────┐
                    │  Spring MVC        │ MethodArgumentNotValidException
                    │  overrides         │ and other framework exceptions
                    └─────────┬──────────┘
                              │ (if no match)
                    ┌─────────▼──────────┐
                    │  Generic fallback  │ Exception → 500, no leak
                    └────────────────────┘
```

---

## The 500 handler

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ProblemDetail> handleGenericException(
        Exception ex, HttpServletRequest request) {

    // Log everything — this is unexpected
    log.error("Unhandled exception at {}", request.getRequestURI(), ex);

    // Return nothing useful to the client — intentional
    ProblemDetail problem = buildProblemDetail(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "An unexpected error occurred. Please try again later.",
        ErrorCode.INTERNAL_ERROR,
        request
    );
    return ResponseEntity.status(500).body(problem);
}
```

Stack traces belong in **logs**, never in HTTP responses.

---

## The shared builder

```java
private ProblemDetail buildProblemDetail(
        HttpStatus status, String detail, String errorCode, HttpServletRequest request) {

    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(
        ERROR_TYPE_BASE + errorCode.toLowerCase().replace('_', '-')));
    problem.setTitle(status.getReasonPhrase());
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("errorCode", errorCode);
    problem.setProperty("traceId", resolveTraceId());
    problem.setProperty("timestamp", Instant.now());
    return problem;
}
```

Every handler calls this — **one place** to change the response shape.

---

## Checklist

- **One** `@RestControllerAdvice` — never scatter `@ExceptionHandler` across controllers
- **Extend** `ResponseEntityExceptionHandler` — don't reinvent Spring MVC handling
- **Typed exceptions** — one class per scenario, not one per HTTP status
- **Error codes** — stable strings for machines, not just HTTP status
- **Never leak** — no stack traces, no SQL, no constraint names to clients
- **Chain causes** — always pass the original exception when wrapping
- **Consistent shape** — every error response has the same fields

---

<!-- _paginate: false -->
<!-- _class: closing -->

# That's it.

### Code: `github.com/javaguy/exception-handling`

> The goal is not to handle exceptions.
> The goal is to make failures *observable, actionable, and safe*.
