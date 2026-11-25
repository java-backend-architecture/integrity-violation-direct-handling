# Handling Data Integrity Violations in Spring Boot + PostgreSQL

A companion project for the article [link to article]. This example demonstrates how to return HTTP 409 for uniqueness violations and HTTP 500 with diagnostic logging for CHECK or NOT NULL violations that duplicate input validation. Constraint and column names are extracted from structured PostgreSQL driver fields (`getConstraint()`, `getColumn()`), rather than parsed from exception messages.

## What the Project Demonstrates

- **Two-layer protection:** Jakarta Validation at the application entry point and named constraints in the database schema.
- **Database-enforced storage invariants:** The repository normalizes values using `trim` and `lower`, while CHECK constraints enforce the normalized-storage invariants regardless of the write path. A direct INSERT test verifies this behavior.
- **Isolated PostgreSQL driver dependency:** `ErrorDetailsExtractor` is the only component directly dependent on `PSQLException`. Violation classification (`IntegrityPolicy`) and HTTP handling (`WebExceptionHandler`) operate on plain strings.
- **Consistent handling across JDBC and JPA:** The decision to return HTTP 409 is based on SQLState `23505`. The same exception handler is tested against both `JdbcClient` and Hibernate/JPA using PostgreSQL.
- **Validation/schema mismatch:** A name consisting of a single emoji passes Java's `@Size(min = 2)` validation but fails PostgreSQL's `length(name) between 2 and 5` constraint.

## Response Policy

| Situation | HTTP response | Log |
|---|---|---|
| Jakarta Validation failure | 400; the request never reaches the database | Not required |
| Uniqueness violation (UNIQUE / PRIMARY KEY) | 409 + indication of what is already taken | info |
| CHECK or NOT NULL violation duplicating input validation | 500 Internal error | error, `reason=validation_schema_mismatch` |
| Other integrity violation (`id = NULL`, business CHECK, FK) | 500 Internal error | error, `reason=integrity_violation` |
| Other database access failure | 500 Database failure | error + stack trace |
| Unexpected error | 500 Internal error | error + stack trace |

Foreign key violations are not covered by this example because the schema contains only one table. A real API requires a separate policy for handling them.

## Environment

| Component | Version |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.2 |
| Zonky embedded-postgres | 2.2.2 |
| PostgreSQL binaries | 19.0.0 (via `embedded-postgres-binaries-bom` 19.0.0) |
| PostgreSQL JDBC Driver | 42.7.14 |

By default, Zonky uses PostgreSQL 14.22 binaries. This project overrides the server version to 19.0.0 through the BOM. The NOT NULL behavior (the column name is reported instead of a constraint name) has been verified against PostgreSQL 14.22 and 19.0.

## Running the Tests

A locally installed PostgreSQL instance is not required. The tests start an embedded PostgreSQL server.

```bash
./mvnw test
```

## Tests

The main tests run against a real PostgreSQL instance and verify actual database constraints.

| Test | What It Verifies |
|---|---|
| `ConstraintNameTest` | Constraint naming contract; NOT NULL reports the column rather than a constraint name; explicit `id = NULL`; a direct INSERT bypassing the repository is rejected by the schema CHECK constraint |
| `ReasonTest` | Violation reason classification (`IntegrityPolicy.reason`) without Spring |
| `ErrorDetailsExtractorTest` | Without the PostgreSQL driver, the extractor returns `unknown` while preserving the SQLState |
| Emoji test | A validation/schema mismatch results in HTTP 500 and the expected log entry |
| `JpaDuplicateTest` | A real Hibernate + PostgreSQL uniqueness violation returns HTTP 409 through the same exception handler |
| Mock repository test | The exception handler does not depend on a specific `DataAccessException` subclass |
| `DbFailureTest` | Generic responses to database failures (using mocks) |

The `JpaUser` entity and repository used by the JPA test are located exclusively in the test sources. The production code uses `JdbcClient`, and the database generates IDs using `default gen_random_uuid()`. In the test configuration, `spring.jpa.hibernate.ddl-auto=none`: the schema is created by `schema.sql`.

## Code Structure

- `ErrorDetailsExtractor` extracts the SQLState, constraint name, and column name from the exception.
- `IntegrityPolicy` classifies violations and defines the messages used in HTTP 409 responses. It consists of static functions and has no Spring dependencies.
- `WebExceptionHandler` maps exceptions to HTTP responses (`ProblemDetail`) and handles logging.
- `UserWriteRepository` writes to the `users` table using `JdbcClient`.

## Limitations

- The length limits are intentionally small to make the tests easy to follow.
- Lowercasing email addresses is an example-specific normalization rule, not a universal property of email addresses.
- The example assumes ASCII email addresses and `C` or `en_US` collations. For other locales, Java and PostgreSQL normalization rules must be aligned separately.
- Returning `Email already exists` can allow account enumeration. Whether this response is appropriate depends on the security requirements of the particular API.
- The direct dependency on the `PSQLException` class is isolated in `ErrorDetailsExtractor`. However, classification relies on PostgreSQL SQLStates (`23505`, `23514`, `23502`), so the policy must be adapted when switching database vendors.
- For schemas with multiple tables, the table name (`getTable()`) should also be included in the classification.
- Batch JDBC operations may expose errors through the `SQLException.getNextException()` chain. The extractor is designed for single operations.
