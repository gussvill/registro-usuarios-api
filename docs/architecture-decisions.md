# Architecture decisions

This document records why the service is built the way it is. Each decision is a short record
with its context, the decision, the alternatives that were discarded and the consequences. It is
self-contained: everything it refers to is in this repository.

The service has one capability: `POST /api/v1/users` registers a user and answers with the stored
user, a signed token and the generated fields. The decisions below follow from that and from the
exercise statement.

## Index

| Id | Decision |
|----|----------|
| [ADR-001](#adr-001-a-light-hexagon-for-a-one-endpoint-service) | A light hexagon for a one-endpoint service |
| [ADR-002](#adr-002-ports-and-adapters-one-reason-each) | Ports and adapters, one reason each |
| [ADR-003](#adr-003-no-inbound-port-interface) | No inbound port interface |
| [ADR-004](#adr-004-transactional-on-the-use-case-is-the-only-framework-concession) | `@Transactional` on the use case is the only framework concession |
| [ADR-005](#adr-005-a-separate-persistence-model-with-write-only-mapping) | A separate persistence model with write-only mapping |
| [ADR-006](#adr-006-validation-lives-in-the-domain) | Validation lives in the domain |
| [ADR-007](#adr-007-strict-json-string-typing) | Strict JSON string typing |
| [ADR-008](#adr-008-the-mensaje-error-contract-instead-of-rfc-9457) | The `{"mensaje"}` error contract instead of RFC 9457 |
| [ADR-009](#adr-009-status-mapping) | Status mapping |
| [ADR-010](#adr-010-an-error-controller-for-errors-forwarded-to-the-error-path) | An error controller for errors forwarded to the error path |
| [ADR-011](#adr-011-spring-boot-411-and-java-17-against-the-statements-java-8) | Spring Boot 4.1.1 and Java 17 against the statement's "Java 8+" |
| [ADR-012](#adr-012-gradle-as-the-build-tool) | Gradle as the build tool |
| [ADR-013](#adr-013-h2-hibernate-and-a-versioned-schemasql-with-validate) | H2, Hibernate and a versioned `schema.sql` with `validate` |
| [ADR-014](#adr-014-jjwt-with-jackson-2-beside-jackson-3) | JJWT with Jackson 2 beside Jackson 3 |
| [ADR-015](#adr-015-bcrypt-through-spring-security-crypto-without-the-security-starter) | BCrypt through `spring-security-crypto`, without the security starter |
| [ADR-016](#adr-016-the-token-is-persisted-in-clear-and-the-default-secret-is-for-development-only) | The token is persisted in clear and the default secret is for development only |
| [ADR-017](#adr-017-a-password-policy-strategy-with-a-deliberately-weak-default) | A password policy Strategy with a deliberately weak default |
| [ADR-018](#adr-018-the-literal-contrycode) | The literal `contrycode` |
| [ADR-019](#adr-019-email-normalisation-and-field-limits) | Email normalisation and field limits |
| [ADR-020](#adr-020-application-generated-identifier-and-a-single-clock-reading) | Application-generated identifier and a single clock reading |
| [ADR-021](#adr-021-quality-gates) | Quality gates |
| [ADR-022](#adr-022-what-was-deliberately-left-out) | What was deliberately left out |

Below the records: [components](#components) and [known limitations](#known-limitations).

---

## ADR-001: A light hexagon for a one-endpoint service

**Context.** By ordinary criteria this service does not need ports and adapters. It has one entry
point, one database, two tables and create-only logic. Without the exercise it would be a
controller, a service and a repository with a request DTO and validation annotations, and that
would be the correct default. Hexagonal architecture pays when the domain must be tested without
infrastructure, when there is more than one entry point or an unstable external dependency, or when
the service will outlive its framework. Only the first of those is true here, and only mildly.

**Decision.** The exercise statement asks for design patterns and good practices to be shown, so
the service is built as a hexagon and kept as light as it can be. Five rules limit the cost:

1. One use case class and no inbound port interface (ADR-003).
2. A port exists only where there is a real, replaceable dependency: the database, hashing and
   token signing (ADR-002). Time uses `java.time.Clock`, not a custom port.
3. No read path. The repository port has two methods and there is no mapping from storage back to
   the domain.
4. No mapper library, no Lombok, no domain events, no CQRS.
5. Every pattern is tied to a variation that exists today (see the table in ADR-002 and the
   patterns listed below).

Patterns in use, and the variation each one absorbs:

| Pattern | Where | Variation it absorbs |
|---------|-------|----------------------|
| Ports and adapters | `domain.port` and `infrastructure.*` | Database, hashing and signing are replaced by fakes in the unit tests |
| Value object | `Email`, `UserId`, `Phone` | Normalisation and validity in one place |
| Strategy | `PasswordPolicy` | The password rule must be configurable and is the one most likely to change |
| Adapter | `UserPersistenceAdapter`, `JjwtTokenIssuer`, `BCryptPasswordHasher` | Third-party interfaces against the ports |
| Builder | `User.Builder` | Three adjacent `String` fields (`name`, `passwordHash`, `token`) invite a positional constructor call that stores a token as a hash |
| Controller advice | `GlobalExceptionHandler` | One place that translates every error |

**Alternatives discarded.**

- Package by layer without a domain (`controller`, `service`, `repository`): simpler and sufficient
  for the behaviour, but it shows none of the requested patterns.
- Nested `adapter/in`, `adapter/out`, `application/port/in`, `application/port/out` packages: four
  more package levels for a handful of classes.
- Factory, Observer, domain events and CQRS: no variation in this service asks for them.

**Consequences.**

- There are more types than the behaviour needs, and the reader has to follow a port to find the
  class behind it. ArchUnit rules and a test that proves each rule fails on a violation keep the
  structure honest (ADR-021).
- The domain is tested in plain JUnit, with no Spring context and no database.
- If the service stays a single endpoint, the layered default would have been cheaper. If a second
  entry point or a second storage appears, the ports are already where the change would go.

## ADR-002: Ports and adapters, one reason each

**Context.** A port is an interface owned by the inner side and implemented from the outside. It is
worth having only if something real stands on the other side.

**Decision.** Three outbound ports live in `domain.port`.

| Port | Adapter | Why the port exists |
|------|---------|---------------------|
| `UserRepository` (`existsByEmail`, `save`) | `UserPersistenceAdapter`, backed by Spring Data JPA; `InMemoryUserRepository` in the test tree | The use case is tested without a database, and a second implementation really exists |
| `PasswordHasher` (`hash`) | `BCryptPasswordHasher`; a fake in the tests | BCrypt is deliberately slow; unit tests must not pay for it |
| `TokenIssuer` (`issue`) | `JjwtTokenIssuer`; a fake in the tests | Keeps JJWT, key handling and its Jackson 2 dependency out of the inner layers |

Time is not a port: `java.time.Clock` is injected directly and tests use `Clock.fixed`.

**Alternatives discarded.**

- Calling `JpaRepository` from the use case: couples the application layer to Spring Data and
  exposes `deleteAll` and `findAll` to code that must only create.
- Calling `BCryptPasswordEncoder` from the use case: puts Spring Security in the application layer.
- A custom `TimeProvider` port: the JDK type already is the abstraction.
- A password-policy provider port: an interface whose only job is to return another interface.
  `PasswordPolicy` is a Strategy and the wiring class builds it from properties.

**Consequences.** Each port has two implementations (production and test), so none is
speculative. The adapters are package-private and independent of each other, which an ArchUnit
rule enforces.

## ADR-003: No inbound port interface

**Context.** Hexagonal diagrams usually show an inbound port in front of the use case.

**Decision.** `UserController` depends on the concrete `RegisterUserUseCase`. An interface with one
implementer adds a file and a hop and removes nothing. The web slice tests replace the class with a
mock.

**Alternatives discarded.** A `RegisterUserPort` interface with a `...Service` implementation.

**Consequences.** If a second entry point (a message consumer, a command line) is added, extracting
an interface from the class is a mechanical change made when the second caller exists.

## ADR-004: `@Transactional` on the use case is the only framework concession

**Context.** The existence check and the insert must belong to one consistent operation, and the
application layer is the natural owner of that boundary.

**Decision.** `RegisterUserUseCase.register` carries Spring's `@Transactional`. This is the only
framework type in the `domain` and `application` packages. The class has no stereotype annotation:
`ApplicationConfig` creates it as a bean.

| Option | Verdict |
|--------|---------|
| `@Transactional` on the use case method | Chosen: one import, recognised at once by any reader |
| A decorator built with `TransactionTemplate` in the configuration | Rejected: it keeps the application layer free of Spring but adds an interface and a class whose only job is to avoid one annotation |
| `@Transactional` on the persistence adapter | Rejected: the existence check and the save would run in separate transactions |
| On the controller | Rejected: it mixes HTTP with consistency |

**Consequences.**

- The concession is fenced by an ArchUnit allow-list: the `application` package may depend only on
  `domain`, itself, `java.*` and `org.springframework.transaction.annotation`. A test shows the
  rule rejecting a `@Service`, a logger and a reach into `infrastructure`.
- The class must stay non-final and the method public, because the annotation works through a
  proxy. A test pins both.
- Hashing runs inside the transaction. That is irrelevant at this scale and is recorded here.
- The success log line lives in the controller because the allow-list leaves no logger in the
  application layer.

## ADR-005: A separate persistence model with write-only mapping

**Context.** The domain aggregate `User` is immutable and validated at construction. JPA wants a
no-argument constructor, mutable fields and non-final classes.

**Decision.** `UserJpaEntity` and `PhoneJpaEntity` live in `infrastructure.persistence` and are
separate from the domain model. Mapping goes one way, from `User` to the entity (`UserJpaEntity.from`).
There is no mapping back, because nothing reads a user in this service.

- Phones are a bidirectional `@OneToMany(mappedBy, cascade = ALL, orphanRemoval = true)` with a
  lazy `@ManyToOne`, ordered by their identity key so submitted order survives a reload.
- `users.id` is assigned by the application (ADR-020), so the entity implements `Persistable` with a
  transient "new" flag. Without it Spring Data would treat an assigned id as an existing row and
  issue a `SELECT` before every insert; a test asserts that no lookup happens.

**Alternatives discarded.**

- Annotating the aggregate: the domain would import `jakarta.persistence`, lose its immutability
  and need a no-argument constructor.
- `@ElementCollection` for phones: a table without a primary key.
- A unidirectional `@JoinColumn` collection: one insert and one update per phone.

**Consequences.** There is mapping code to write and test, but it is write-only and short. Because
the response is built from the in-memory aggregate and not from a reload, the data returned and the
data stored are identical by construction, which a test also checks against the stored row.

## ADR-006: Validation lives in the domain

**Context.** A request can break several rules at once, and the error contract asks for every
broken field in one message, at most one message per field, taking the first failing rule in the
order required, then length, then format. The two formats (email and password) are runtime
configuration.

**Decision.** Each rule is a pure function in the domain that returns a typed reason (`Email.violation`,
`User.nameViolation`, `Phone.violations`, `PasswordPolicy.violation`). The use case gathers the
reasons into a set, and a non-empty set becomes one `InvalidUserDataException` before the repository
is touched. The value objects call the same functions in their constructors, so an `Email` cannot
exist in an invalid state when it is built through another path. The domain carries no client text:
`ErrorMessages` in the web layer maps each reason to its message. The request records carry no Bean
Validation annotations.

| Option | Verdict |
|--------|---------|
| Rules as pure functions in the domain, collected by the use case | Chosen: one definition per rule, ordering is plain code, domain tests need no framework |
| Built-in Bean Validation constraints | Rejected: all constraints of a field fire together and unordered, so a blank email would report both "required" and "format" |
| `@GroupSequence` (required, then length, then format) | Rejected: the sequence is global, so once any field fails the first group the later groups are skipped for every field |
| One custom constraint per field that delegates to the domain | Rejected: the same logic wrapped in eight annotation types with Spring-injected patterns |

**Consequences.**

- Length always precedes the pattern, so an oversized value never reaches the regular expression
  engine. A test sends a 50,000-character email against a pattern prone to catastrophic
  backtracking and requires an answer within five seconds.
- The "required" and byte-length rules of the password sit in the domain outside the Strategy, so
  the 72-byte bound holds whatever pattern an operator configures.
- Bean Validation is still used, for the two configuration records, so a bad property stops the
  start-up.

## ADR-007: Strict JSON string typing

**Context.** By default the JSON mapper turns `"name": 123` into the string `"123"`. A client that
sends the wrong type should get a 400, not a silently converted value.

**Decision.** `JacksonConfig` registers a mapper customizer that fails the coercion of an integer,
a float or a boolean into a textual property. Arrays and objects sent for a string, and a scalar
sent for a phone entry, already fail by default.

A wrongly typed body fails during binding, before the controller or any domain rule runs, so the
answer is the generic body message and never a field message.

**Alternatives discarded.** A custom deserializer per field (repeated on six fields and easy to
forget on a new one); reading the body as a tree and inspecting it by hand (bypasses data binding and
the published schema).

**Consequences.** One class covers every string field. A digit string such as `"123"` is still a
valid string. A JSON `null` reaches the use case as a missing value.

## ADR-008: The `{"mensaje"}` error contract instead of RFC 9457

**Context.** Spring Boot can answer errors as RFC 9457 problem details. The exercise statement
fixes a different shape: a JSON object with a single `mensaje` key, and the exact text
`El correo ya registrado` for a duplicate email.

**Decision.** Every error answers `{"mensaje": "<text>"}` with a JSON content type. One
`@RestControllerAdvice`, `GlobalExceptionHandler`, extends `ResponseEntityExceptionHandler`, so
every standard Spring MVC exception goes through one overridable method and the shape is replaced in
one place. The statement fixes only the duplicate text; the other messages are written in Spanish
to match it, are listed in `ErrorMessages` and never contain the rejected value.

**The 406 rule.** If a client sends `Accept: application/xml`, writing the error body would itself
be negotiated against that header, fail again and end as an empty 406. Every error response sets a
concrete `Content-Type`, which makes Spring skip negotiation and write JSON. A test fails without it.

**Alternatives discarded.** `spring.mvc.problemdetails.enabled`: standard, but the statement mandates
the other shape. A custom `ErrorAttributes` bean: the stock error controller still negotiates, so a
browser would get HTML and a 406 an empty body.

**Consequences.** The error shape is not the standard one, and the service says so rather than
mixing both. The OpenAPI document, Swagger UI and the H2 console are tools, not API responses, and
keep their own formats.

## ADR-009: Status mapping

| Situation | Status | `mensaje` |
|-----------|--------|-----------|
| Registered | 201 | (the user) |
| One or more fields break a rule | 400 | the messages of the broken fields, distinct, sorted, joined with `"; "` |
| Body unreadable, malformed, empty, wrongly typed | 400 | `El cuerpo de la solicitud no es válido` |
| Any other 400 raised inside the application | 400 | `La solicitud no es válida` |
| Unknown route | 404 | `Recurso no encontrado` |
| Method not allowed on a known route (`Allow` header kept) | 405 | `Método no permitido` |
| `Accept` cannot be satisfied | 406 | `Formato de respuesta no aceptable` |
| `Content-Type` is not JSON | 415 | `Tipo de contenido no soportado` |
| Email already registered | 409 | `El correo ya registrado` |
| Anything unexpected | 500 | `Error interno del servidor` |

**Decisions inside the table.**

- 409 for a duplicate, not 400 or 422: the conflict is with the state of the resource, not with the
  shape of the request.
- Messages are sorted on the Spanish text and joined with `"; "`, so the response is deterministic
  and a test can compare it exactly. A repeated reason appears once.
- The 500 body is fixed text. The stack trace goes to the server log only, and it is never built
  from the exception message.
- Any 4xx without a specific row answers `La solicitud no es válida` and keeps its status; a 413 or
  a 431 is not announced as an internal error.

**Consequences.** The same mapping is shared by the advice and the error controller through
`ErrorMessages.forStatus`, so the two cannot drift apart.

## ADR-010: An error controller for errors forwarded to the error path

**Context.** Some errors never reach a Spring MVC handler method: a filter that calls `sendError`,
or any error the servlet container forwards to its error page. Boot's stock `/error` handler
negotiates its content, so it can answer an HTML page or an empty body.

**Decision.** `ApiErrorController` replaces Boot's `/error` handler. Errors forwarded to `/error` get
the same JSON body as every other error, for every method and every `Accept` value, with an explicit
JSON content type. The controller is hidden from the OpenAPI document.

**Alternatives discarded.**

- A Tomcat error-report valve, to also answer in JSON the requests Tomcat rejects before any web
  application is chosen (the request `GET /api/v1/users/%zz` was the case). It was removed: it is
  specific to Tomcat, it depends on the order of the valves in Boot's host pipeline, and it could
  only cover part of the container's rejections, because the HTTP parser answers a malformed
  request line or oversized headers before any valve runs. Two classes, an ordering rule and
  raw-socket tests bought a partial guarantee, so the contract states its boundary instead.
- `ErrorAttributes` only: see ADR-008.

**Consequences.**

- **Limit.** A request that the servlet container rejects before any application code runs (an
  invalid percent-escape in the path, a malformed request line, oversized headers) is answered by
  the container's own error page, normally HTML, and is outside the `mensaje` contract. This is
  recorded in the known limitations and the README.
- The forwarded-error cases are tested over a raw socket, because MockMvc does not perform the
  container's error dispatch.

## ADR-011: Spring Boot 4.1.1 and Java 17 against the statement's "Java 8+"

**Context.** The statement asks for Java 8 or later. Spring Boot 3 and 4 need at least Java 17. The
last Boot line that runs on Java 8 (2.7) no longer receives open-source updates.

**Decision.** Spring Boot 4.1.1 on a Java 17 toolchain. Java 17 is "8 or later" and is the lowest
version a supported Boot line runs on, so the service stays on a maintained framework without
requiring a newer JDK than necessary. The Gradle toolchain makes the build use JDK 17 regardless of
the JDK that starts Gradle, and the `foojay-resolver-convention` plugin in `settings.gradle` lets
Gradle download a JDK 17 when none is installed.

**Alternatives discarded.** Boot 2.7 on Java 8: matches the statement literally but starts the
project on an unmaintained line. A newer Java: needlessly raises what a reviewer must install.

**Consequences.**

- Reviewers need any JDK 17 or newer to launch Gradle, which downloads a JDK 17 toolchain if none is
  installed, or Docker. The README says so first.
- Error Prone is pinned to 2.42.0, the last line that runs on JDK 17 (ADR-021).
- Some third-party types have not caught up with the Boot 4 generation. JJWT still brings Jackson 2
  (ADR-014).

## ADR-012: Gradle as the build tool

**Context.** The statement does not name a build tool.

**Decision.** Gradle with the Groovy DSL, through the committed wrapper (9.7.1). One `./gradlew build`
compiles with Error Prone, checks formatting, runs every test and enforces the coverage gate. The
Gradle plugin for Spring Boot builds the executable jar, named `app.jar` so the image's entry point
does not change with the project version.

**Alternatives discarded.** Maven would have worked equally well. Gradle was chosen as a preference:
the wrapper and the Java toolchain select the JDK reproducibly, and the plugins used here (Spring
Boot, JaCoCo, Spotless, Error Prone) configure in a short build file. It is not a technical
necessity.

**Consequences.** Nothing but a JDK 17 or newer is needed to launch the build: the wrapper downloads
the exact Gradle version, and the toolchain resolver downloads a JDK 17 if none is installed. The
Dockerfile uses the same wrapper, so the image and the local build use one toolchain.

## ADR-013: H2, Hibernate and a versioned `schema.sql` with `validate`

**Context.** The statement asks for an in-memory database and a script that creates it.

**Decision.**

- H2 in memory, with the H2 console enabled for inspection, and Hibernate as the JPA provider.
- The schema is a versioned script, `src/main/resources/schema.sql`, run by Spring at start-up.
  Hibernate only validates it: `spring.jpa.hibernate.ddl-auto=validate`. The setting is explicit
  because with an embedded database Boot otherwise defaults to `create-drop`, which would make the
  script decorative.
- `spring.jpa.open-in-view=false`, so no persistence session outlives the use case.
- Timestamps are `TIMESTAMP(6) WITH TIME ZONE`, so the stored value is a UTC instant at
  microsecond precision.

**What `validate` checks and what it does not.** It checks that every mapped table and column exists
and that the column type is compatible with the mapped Java type. A column declared `INTEGER` where
a string is mapped fails the start-up. It does **not** distinguish `TIMESTAMP` from
`TIMESTAMP WITH TIME ZONE`. That part of the schema is proved by tests that round-trip instants at
microsecond precision and compare the stored value with the response.

**Alternatives discarded.** `create-drop`: no reviewable script. Flyway or Liquibase: correct for a
real service with a persistent database, but a dependency too many for one in-memory script.

**Consequences.** The script is the single source of the schema and is checked by tests that boot
against it (columns at their maximum length, the `UNIQUE` constraint on the email, the foreign key
from phones to users). The script is idempotent (`IF NOT EXISTS`). Data does not survive a restart.
Moving to a persistent database would add a migration tool and replace H2.

## ADR-014: JJWT with Jackson 2 beside Jackson 3

**Context.** Spring Boot 4.1 serialises JSON with Jackson 3 (`tools.jackson`). JJWT 0.13.0 serialises
its claims with Jackson 2 (`com.fasterxml.jackson`) through its `jjwt-jackson` module.

**Decision.** Use JJWT and let the two Jackson generations coexist. The two do not interact: Spring
MVC uses Jackson 3 for requests and responses, and JJWT uses Jackson 2 privately to write the claims
of the token. The annotations the records use (`@JsonProperty`, `@JsonPropertyOrder`) stay in the
`com.fasterxml.jackson.annotation` package for Jackson 3 as well.

The token is HS256 (one service issues it and nothing verifies it). The claims are `sub` (the user
id), `email`, `iat` and `exp`. The signing key is built from the raw bytes of the secret, and the
`JjwtTokenIssuer` constructor refuses a secret shorter than 32 bytes or a non-positive expiration
with a message that names the property and never the value, so the application does not start
rather than answer 500 on the first registration.

**Alternatives discarded.**

- RS256 or ES256: right once another party verifies tokens; here it is key management for no consumer.
- Hashing an arbitrary passphrase to 256 bits: it would turn a four-character secret into a "valid"
  key and hide the weakness.
- `jjwt-gson` or hand-written JWT code: possible, but JJWT is the common choice and a test proves the
  pair works.

**Consequences.** Two Jackson generations are on the runtime classpath. A full-context test parses a
token from a response written by Jackson 3 with JJWT, so the coexistence is checked on every build.
Dropping Jackson 2 later means replacing `jjwt-jackson`.

## ADR-015: BCrypt through `spring-security-crypto`, without the security starter

**Context.** The password must be stored as a salted hash and never returned.

**Decision.** `BCryptPasswordHasher` uses `BCryptPasswordEncoder` with strength 12 from
`spring-security-crypto`, the small module with no web or servlet dependency.
`spring-boot-starter-security` is not added: its default filter chain would answer 401 outside the
error contract, and CSRF protection would block the `POST`.

**Alternatives discarded.** The security starter with a permissive configuration (a larger surface to
configure and to get wrong for one hash function); Argon2 (stronger on paper, needs a native or
additional library, and BCrypt is adequate here).

**Consequences.** BCrypt only uses the first 72 bytes of a password, so the domain rejects longer
ones with a 400 instead of silently truncating (ADR-006). The class that wraps the encoder is the
only place that knows the algorithm.

## ADR-016: The token is persisted in clear and the default secret is for development only

**Context.** The statement requires the token to be persisted with the user. A token stored in clear
is a bearer credential at rest, which would normally be avoided.

**Decision.** The token is stored as issued, in `users.token VARCHAR(1024)`. A test checks that the
longest possible token (a 254-character email) fits. The token is not validated on any request, so
nothing in the service depends on the stored value.

The secret and the expiration are properties: `app.token.secret` (environment variable
`TOKEN_SECRET`) and `app.token.expiration` (default 15 minutes, must be positive). The secret has a
default so that a reviewer can run the service with no setup, and the default is labelled
"DEV ONLY" in `application.properties` and in the README.

| Option | Verdict |
|--------|---------|
| A published development-only default | Chosen: the service runs at once, the weakness is stated, and a short secret fails the start-up |
| No default | Rejected: the reviewer cannot just run it |
| A random key at each start | Rejected: the reviewer cannot verify a token with a known secret |

**Consequences.** Anyone who reads the repository knows the default secret. A deployment must set
`TOKEN_SECRET`. A leaked database leaks valid tokens until they expire. Storing a hash of the token
would defeat the requirement that it is persisted for later use, so it is not done.
Secrets are not logged at any level: the records that carry a password, a token or a secret redact
it in `toString()`, the single success log line uses a masked email, and tests capture the output at
DEBUG and TRACE and look for the password, the hash, the token and the secret.

## ADR-017: A password policy Strategy with a deliberately weak default

**Context.** The statement makes the password rule configurable, and its example password is
`hunter2`.

**Decision.** `PasswordPolicy` is an interface (a Strategy) with `RegexPasswordPolicy` as the
implementation built from `app.registration.password-pattern`. The default is

```
^(?=.*[A-Za-z])(?=.*[0-9])\S{7,72}$
```

At least one letter and one digit, 7 to 72 characters without whitespace. It accepts `hunter2` and
rejects `abc12`. It is weak on purpose, because the statement's example has to pass.

**Hardening.** One property replaces it. For example, twelve or more characters with a lower-case
letter, an upper-case letter, a digit and a symbol:

```
^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9\s])\S{12,72}$
```

The 72-character and 72-byte bounds are enforced by the domain whatever the pattern is.

**Alternatives discarded.** A `Pattern` field in the use case: it works but hides the one extension
point the statement names. A symmetric `EmailPolicy` interface: the email format will always be one
regular expression, so a `Pattern` parameter is enough and an abstraction without a second
implementation is not added. The asymmetry is deliberate.

**Consequences.** The default accepts weak passwords; the weakness is documented in the README and
here. An invalid regular expression stops the start-up with a message that names the property.

## ADR-018: The literal `contrycode`

**Context.** The statement spells the phone's country field `contrycode`.

**Decision.** The field is spelled exactly so in requests, responses and the OpenAPI document. The
Java component is `countryCode` with `@JsonProperty("contrycode")`, and the column is
`country_code`. The typo is kept because it is the wire contract, not corrected silently.

**Alternatives discarded.** Accepting both spellings: it doubles the contract for a typo, and a
client would never know which one is the real one.

**Consequences.** A client that sends `countrycode` has it ignored as an unknown property, and the
required `contrycode` is then reported as missing.

## ADR-019: Email normalisation and field limits

**Decision.**

- The email is lower-cased with `Locale.ROOT` (so a Turkish default locale cannot alter it) and is
  stored lower-cased. Two addresses that differ only in case are the same address, which makes
  uniqueness case-insensitive. The length limit is measured after lower-casing, because some
  characters expand.
- The email is **not trimmed**. A value with a leading or trailing space is not blank, reaches the
  pattern and fails it. The pattern is matched with `matches()`, so a trailing newline is rejected
  even though `$` alone would tolerate it. Patterns are therefore written in lower case, since they
  apply to the lower-cased value.
- Names and phone fields are stored exactly as received.
- Phone fields are strings, so leading zeros and `+` survive.
- `phones` may be absent, null or empty; it is returned as `[]`.

| Field | Limit |
|-------|-------|
| `name` | 255 characters |
| `email` | 254 characters (after lower-casing) |
| `password` | 72 characters and 72 UTF-8 bytes |
| `phones` | 10 entries |
| `number` | 20 characters |
| `citycode`, `contrycode` | 10 characters each |

The limits are domain constants. The validation, the JPA column lengths and the OpenAPI
`maxLength` reference them, and `schema.sql` mirrors them; tests store every column at its maximum
length against the script, so a mismatch fails the build.

**Alternatives discarded.** `jakarta.validation.constraints.Email` accepts `juan@dominio`, which the
format rule rejects. Trimming input hides a client bug.

**Consequences.** The limits are assumptions, because the statement gives none. Every field has a
bound so hostile input cannot reach the regular expression engine or the database unbounded.

## ADR-020: Application-generated identifier and a single clock reading

**Decision.**

- The user id is a random UUID generated by the application (`UserId.generate()`), because the
  token's `sub` claim needs the id before the row exists. A database-generated id would need a
  second write to store the token.
- The use case reads the clock **once**: `clock.instant()` truncated to microseconds. That one value
  is passed to the token issuer and to the builder, which assigns it to `created`, `modified` and
  `last_login`. Microseconds are the precision of the `TIMESTAMP(6)` columns, so the response, the
  token and the stored row show the same instant, and `iat` is that instant at second precision.
- The clock is a `java.time.Clock` bean, so tests fix it.

**Consequences.** There is no drift between the response and the database. The use case's reading
of the clock is pinned by a test that uses a clock which advances on every read.

## ADR-021: Quality gates

| Gate | What it enforces | Notes |
|------|------------------|-------|
| JaCoCo | At least 80 % line coverage over `domain` and `application` | Measured value: 100 % (197 of 197 lines). Infrastructure is exercised by slice and full-context tests but is not gated: a percentage on wiring code invites tests of configuration |
| Spotless | Google Java Format 1.28.0, no unused imports, no trailing whitespace | `spotlessCheck` runs inside `check`; `./gradlew spotlessApply` fixes it |
| Error Prone | Static analysis in the Java compiler | Pinned to 2.42.0, the last line that runs on JDK 17 (ADR-011); it ran on this build and reported real diagnostics. Together with `-Xlint:all -Werror` on the main sources |
| ArchUnit (core) | Layering and dependency rules | Seven rules, listed below |
| `.editorconfig` | UTF-8, LF, final newline, indentation | Shared by editors |

The ArchUnit rules:

1. `domain` imports none of Spring, Jakarta, Hibernate, either Jackson generation, JJWT or Swagger.
2. `application` depends only on `domain`, itself, `java.*` and the transaction annotation.
3. Layers point inward: the domain does not depend on `application` or `infrastructure`, and the
   application does not depend on `infrastructure`.
4. `web`, `persistence`, `security` and `config` do not depend on each other.
5. No class uses field injection.
6. `@Entity` classes reside in `infrastructure.persistence`.
7. No class in `web` uses a JPA entity.

**A rule that cannot fail is not a rule.** A second test class feeds each rule a fixture class written
to break it (22 files in a separate test package) and requires the rule to fail naming that class.
An empty set of offenders would otherwise pass vacuously, as happens with a mistyped package
pattern; ArchUnit's own "no classes selected" failure catches that case as well.

The core ArchUnit library is used from ordinary tests; `archunit-junit5` is not, because its engine
targets the JUnit 5 platform and Boot 4.1.1 manages JUnit 6.

**Consequences.** `./gradlew build` is the single gate and CI runs it. The gates cost build time
(about 25 seconds on a clean build with the whole suite) and a stricter compile, which is the point.

## ADR-022: What was deliberately left out

| Left out | Reason |
|----------|--------|
| Login endpoint and token validation on requests | Not requested; the token is issued and stored only |
| Update, delete and list of users | Not requested; the repository port has two methods |
| Spring Security starter | See ADR-015 |
| Actuator and health endpoints | Not requested; the image has no `HEALTHCHECK` on purpose, so whoever runs the container decides how to probe it |
| Flyway or Liquibase | See ADR-013 |
| Mapper libraries, Lombok | Mapping is small; the response is a record, so a forgotten field is a compile error |
| Domain events, CQRS, Factory, Observer | No variation in the service asks for them |
| `Location` header on 201 | There is no `GET` for the resource; a link to a 404 would mislead |
| Rate limiting, TLS, CORS | Deployment concerns, outside the exercise |
| Mutation testing, dependency vulnerability scanning | Outside the exercise; worth adding in a real pipeline |

---

## Components

| Component | Responsibility | Why it exists |
|-----------|----------------|---------------|
| `User` (+ `Builder`) | Aggregate of a registered user; immutable after `build()`; checks its invariants | One place that says what a valid registered user is; the builder names every value |
| `UserId` | Typed identifier around a UUID | Stops an id from being mixed with another string; generated before the row exists |
| `Email` | Value object: lower-cased, bounded, format-checked, masked for logs | One definition of normalisation and uniqueness |
| `Phone` | Value object: number, city code, country code | Keeps the three strings together with their limits |
| `PasswordPolicy`, `RegexPasswordPolicy` | Strategy for the password rules; the regular expression comes from configuration | The statement requires the rule to be configurable |
| `InvalidUserDataException`, `EmailAlreadyRegisteredException` | Typed domain rejections; no client text | The domain says what went wrong, the web layer decides how to say it |
| `UserRepository`, `PasswordHasher`, `TokenIssuer` | Outbound ports | The use case is tested without a database, BCrypt or JJWT (ADR-002) |
| `RegisterUserUseCase`, `RegisterUserCommand` | Orchestrates the registration and owns the transaction | The one application service; the command redacts the password in `toString()` |
| `UserController`, `UserApi` | HTTP to command to response; the interface carries the OpenAPI annotations | Keeps the controller at a few lines |
| `RegisterUserRequest`, `PhoneRequest`, `UserResponse`, `PhoneResponse`, `ErrorResponse` | JSON records | Explicit wire contract; the response has no password component |
| `UserWebMapper` | Static mapping between records and command or aggregate | A forgotten field is a compile error; it never reads the password hash |
| `GlobalExceptionHandler` | The single `@RestControllerAdvice` | One translation point for every error (ADR-008) |
| `ErrorMessages` | The message catalogue and the status lookups | Client text in one place, shared by the two error paths |
| `ApiErrorController` | JSON body for errors forwarded to the error path | ADR-010 |
| `JacksonConfig` | Strict string typing | ADR-007 |
| `UserPersistenceAdapter`, `UserJpaRepository`, `UserJpaEntity`, `PhoneJpaEntity` | Implement `UserRepository` with JPA; translate a unique violation into the domain rejection | ADR-005 and the concurrency rule: the constraint, not the pre-check, guarantees uniqueness |
| `BCryptPasswordHasher` | Implements `PasswordHasher` | ADR-015 |
| `JjwtTokenIssuer`, `TokenProperties` | Implements `TokenIssuer`; binds `app.token.*`; fails the start-up on a weak secret | ADR-014 and ADR-016 |
| `ApplicationConfig`, `RegistrationProperties`, `OpenApiConfig` | Wiring of the use case, the policy and the clock; typed `app.registration.*` properties; OpenAPI metadata | The domain and application classes carry no stereotype annotation, so the wiring is here |
| `schema.sql` | Creates `users` and `phones` | ADR-013 |

---

## Known limitations

- **Container-level rejections.** A request that the servlet container rejects before any
  application code runs (an invalid percent-escape in the path, a malformed request line, oversized
  headers) is answered by the container's own error page, normally HTML, and is outside the
  `mensaje` contract (ADR-010). Everything that reaches the application, including errors forwarded
  to the error path, is covered.
- **Development-only secret and clear-text token.** The default `app.token.secret` is public. The
  token is stored in clear because the statement requires it to be persisted (ADR-016). Set
  `TOKEN_SECRET` for any real use.
- **Weak default password pattern.** It accepts the statement's example, `hunter2` (ADR-017).
- **In-memory data.** Everything is lost when the process stops.
- **Development tools are on by default.** The H2 console (`/h2-console`) and Swagger UI expose the
  database and the API description. Disable them with `spring.h2.console.enabled=false` and
  `springdoc.swagger-ui.enabled=false` outside development.
- **Email enumeration.** The 409 for a duplicate tells a caller whether an address is registered.
  The statement requires that answer.
- **Pragmatic email format.** The default pattern is not RFC 5322: it accepts ordinary addresses and
  rejects values such as `juan@dominio` that have no top-level label. No confirmation e-mail is sent.
- **Unexpected failures can quote data in the server log.** The 500 handler logs the stack trace,
  which includes the database's message. The domain caps every length, so a value-too-long error
  cannot come from valid input, but a message of an unforeseen failure is not filtered. The one
  expected case, a duplicate that reaches the unique constraint, is silenced at its source
  (`logging.level.org.hibernate.orm.jdbc.error=OFF`): Hibernate would otherwise log the clear-text
  address at WARN. Switching that logger off hides Hibernate's own report of a failed statement; the
  failure itself is still raised, translated or logged by the exception handler.
- **Hibernate `validate` and time zones.** It does not tell `TIMESTAMP` from
  `TIMESTAMP WITH TIME ZONE`; that part of the schema is proved by round-trip tests (ADR-013).
- **Hashing inside the transaction.** BCrypt at strength 12 holds a database connection for its
  duration. Irrelevant at this scale (ADR-004).
- **No authentication, rate limiting or TLS.** Registration is open to any caller.
