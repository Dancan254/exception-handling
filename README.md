# Spring Boot Exception Handling — Custom ErrorResponse Branch

A hands-on reference project demonstrating production-grade exception handling in Spring Boot using a **custom `ErrorResponse` DTO** — the most common, widely-understood pattern you will find in real-world Spring Boot APIs.

> This is the `custom-error-response` branch. It implements the simpler DTO approach deliberately, so you can understand it thoroughly before comparing it to the RFC 9457 `ProblemDetail` approach on other branches.

> Built by [@Dancan254](https://github.com/Dancan254) as a teaching resource. Clone it, run it, and experiment with every endpoint while reading the guide.

---

## What This Branch Covers

| Topic | What You Will Learn |
|---|---|
| Custom `ErrorResponse` record | Designing a clean, reusable error body without framework magic |
| `@RestControllerAdvice` | Centralising all exception handling in one class |
| Per-type `@ExceptionHandler` methods | Explicit, readable handler per exception type |
| Validation error handling | Extracting field-level errors from `MethodArgumentNotValidException` |
| Exception chaining | Wrapping infrastructure failures without losing stack traces |
| Catch-all safety net | Preventing internal detail leaks on unexpected exceptions |
| `@JsonInclude(NON_NULL)` | Omitting optional fields (like `errors`) from non-validation responses |

---

## Quick Start

**Prerequisites:** Java 21+, Maven

```bash
git clone https://github.com/Dancan254/exception-handling.git
cd exception-handling
git checkout custom-error-response
./mvnw spring-boot:run
```

The application starts on `http://localhost:8080` with an in-memory H2 database pre-seeded with two users and two products.

**H2 Console:** `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:exceptiondb`
- Username: `sa` / Password: *(empty)*

---

## Error Response Shape

Every non-2xx response from this application returns this JSON body:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "User with id '99' was not found",
  "path": "/api/users/99",
  "timestamp": "2026-05-11T10:15:30.123"
}
```

For validation failures, a `errors` map is added and `message` describes the overall failure:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "One or more request fields failed validation.",
  "path": "/api/users",
  "timestamp": "2026-05-11T10:15:30.123",
  "errors": {
    "email": "must be a well-formed email address",
    "name": "must not be blank"
  }
}
```

---

## Seeded Data

| Resource | Details |
|---|---|
| User 1 | Alice Johnson — `alice@example.com` — **active** |
| User 2 | Bob Smith — `bob@example.com` — **inactive** |
| Product 1 | Pro Laptop 15 — SKU `LAPTOP-001` — stock: 10 |
| Product 2 | SmartPhone X — SKU `PHONE-001` — stock: 0 |

Use these to exercise every exception scenario without any manual setup.

---

## API Endpoints

### Users — `/api/users`

| Method | Path | What it demonstrates |
|---|---|---|
| `POST` | `/api/users` | Bean Validation → 400; email conflict → 409 |
| `GET` | `/api/users` | Normal 200 list |
| `GET` | `/api/users/{id}` | Not found → 404 |
| `PATCH` | `/api/users/{id}/deactivate` | Business rule violation → 422 |
| `DELETE` | `/api/users/{id}?admin=true` | `ForbiddenException` → 403 |
| `GET` | `/api/users/legacy/{id}` | `ResourceGoneException` → 410 |
| `GET` | `/api/users/search?email=` | Inline `ResponseStatusException` → 400 |

### Products — `/api/products`

| Method | Path | What it demonstrates |
|---|---|---|
| `POST` | `/api/products` | SKU conflict → 409 |
| `GET` | `/api/products` | Normal 200 list |
| `GET` | `/api/products/{id}` | Not found → 404 |
| `GET` | `/api/products/{id}/price-check` | `ExternalServiceException` with chaining → 502 |

### Orders — `/api/orders`

| Method | Path | What it demonstrates |
|---|---|---|
| `POST` | `/api/orders` | Inactive user → 422; insufficient stock → 422 |
| `GET` | `/api/orders` | Normal 200 list |
| `GET` | `/api/orders/{id}` | Not found → 404 |
| `PATCH` | `/api/orders/{id}/cancel` | Invalid state transition → 422 |

---

## Project Structure

```
src/main/java/com/javaguy/exceptionhandling/
├── config/
│   └── DataInitializer.java          # Seeds H2 with demo users and products
├── controller/
│   ├── UserController.java           # Demonstrates all exception types
│   ├── ProductController.java        # Demonstrates ExternalServiceException
│   └── OrderController.java          # Demonstrates business rule exceptions
├── domain/
│   ├── entity/                       # JPA entities: User, Product, Order
│   └── repository/                   # Spring Data repositories
├── dto/
│   ├── request/                      # Validated input records
│   └── response/
│       ├── ErrorResponse.java        # Custom error body (status, error, message, path, timestamp, errors)
│       ├── OrderResponse.java
│       ├── ProductResponse.java
│       └── UserResponse.java
├── exception/
│   ├── ResourceNotFoundException.java   # 404
│   ├── ResourceGoneException.java       # 410
│   ├── ConflictException.java           # 409
│   ├── BusinessRuleException.java       # 422
│   ├── ForbiddenException.java          # 403
│   └── ExternalServiceException.java    # 502
├── handler/
│   └── GlobalExceptionHandler.java   # @RestControllerAdvice — one handler per exception type
└── service/
    ├── UserService.java
    ├── ProductService.java
    └── OrderService.java
```

---

## Tech Stack

| | |
|---|---|
| **Framework** | Spring Boot 4 |
| **Language** | Java 21 |
| **Database** | H2 (in-memory) |
| **ORM** | Spring Data JPA / Hibernate |
| **Validation** | Jakarta Bean Validation |
| **Error format** | Custom `ErrorResponse` DTO |
| **Boilerplate** | Lombok |

---

## Learning Guide

For the full architecture walkthrough, how the custom DTO pattern compares to RFC 9457 `ProblemDetail`, and common pitfalls read **[EXCEPTION_HANDLING_GUIDE.md](EXCEPTION_HANDLING_GUIDE.md)**.
