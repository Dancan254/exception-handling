# Spring Boot Exception Handling Masterclass

A hands-on reference project demonstrating production-grade exception handling in Spring Boot 4 — from a simple custom `ErrorResponse` DTO all the way to a fully RFC 9457-compliant `ProblemDetail` architecture.

> Built by [@Dancan254](https://github.com/Dancan254) as a teaching resource. Clone it, run it, and experiment with every endpoint while reading the guide.

---

## Quick Start

**Prerequisites:** Java 21+, Maven

```bash
git clone https://github.com/Dancan254/exception-handling.git
cd exception-handling
./mvnw spring-boot:run
```

The application starts on `http://localhost:8080` with an in-memory H2 database pre-seeded with two users and two products.

**H2 Console:** `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:exceptiondb`
- Username: `sa` / Password: *(empty)*

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
│   └── response/                     # Output records with static factory methods
├── exception/
│   ├── base/AppException.java        # Abstract root of the exception hierarchy
│   ├── ErrorCode.java                # Central registry of machine-readable codes
│   ├── ResourceNotFoundException.java
│   ├── ResourceGoneException.java
│   ├── ConflictException.java
│   ├── BusinessRuleException.java
│   ├── ForbiddenException.java
│   └── ExternalServiceException.java
├── handler/
│   └── GlobalExceptionHandler.java   # Single RFC 9457-compliant handler
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
| **Language** | Java 25 |
| **Database** | H2 (in-memory) |
| **ORM** | Spring Data JPA / Hibernate |
| **Validation** | Jakarta Bean Validation |
| **Error format** | RFC 9457 (`ProblemDetail`) |
| **Boilerplate** | Lombok |

---

## Learning Guide

For the full architecture walkthrough, alternative patterns, and common pitfalls read **[EXCEPTION_HANDLING_GUIDE.md](EXCEPTION_HANDLING_GUIDE.md)**.
