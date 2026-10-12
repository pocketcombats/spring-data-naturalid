# Spring Data Natural ID

[![Maven Central](https://img.shields.io/maven-central/v/com.pocketcombats/spring-data-naturalid)](https://central.sonatype.com/artifact/com.pocketcombats/spring-data-naturalid)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

Cache-aware `findByEmail`-style lookups for Spring Data JPA.

Spring Data JPA runs a SQL query on every call of `findByEmail(...)`, `findBySku(...)` or `findByCode(...)`, even when
the entity is already loaded in the current Hibernate session. This library routes such repository methods to
Hibernate's natural ID API. Repeated lookups by a unique business key are then answered from the session cache, and
optionally from the second-level cache, instead of the database.

- **One runtime dependency, no repository code changes.** `Optional<User> findByEmail(String email)` stays as it is.
- **One requirement:** the field you look up has Hibernate's `@NaturalId` annotation.
- **Nothing else changes.** Only `findByX` / `getByX` methods for the natural ID attribute are affected. All other
  repository methods stay normal Spring Data query methods.
- **Spring Boot 4.1, Spring Data JPA 4, Hibernate 7.3 or newer.**

## The problem: the same query runs again and again

A typical Spring Data JPA repository:

```java
public interface UserRepository extends Repository<User, Long> {

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);
}
```

One transaction, several services, and each service needs the current user:

```java
@Transactional
public void placeOrder(String email, OrderRequest request) {
    User user = userRepository.findByEmail(email).orElseThrow();   // SQL
    pricingService.applyDiscounts(email, request);                  // calls findByEmail again: SQL
    inventoryService.reserve(email, request);                       // again: SQL
    notificationService.notify(email);                              // again: SQL
}
```

The SQL log shows the same statement four times:

```text
select u1_0.id,u1_0.email,u1_0.name from users u1_0 where u1_0.email=?
select u1_0.id,u1_0.email,u1_0.name from users u1_0 where u1_0.email=?
select u1_0.id,u1_0.email,u1_0.name from users u1_0 where u1_0.email=?
select u1_0.id,u1_0.email,u1_0.name from users u1_0 where u1_0.email=?
```

Call `findById(userId)` four times in the same transaction and you get one query. Calls two to four are answered from
the persistence context. `findByEmail` does not get this help, and the Hibernate second-level cache does not help it
either.

### Why `findById` is cached and `findByEmail` is not

- The persistence context (Hibernate's first-level cache) is a map from **primary key** to entity. The second-level
  entity cache is also keyed by primary key. Only lookups by primary key check these caches first:
  `EntityManager.find`, `getReference`, and `findById`, which calls `find`.
- `findByEmail` is a derived query method. Spring Data turns the method name into a JPQL query:
  `select u from User u where u.email = ?1`. Hibernate cannot know which rows match without asking the database, so it
  runs the query every time. When the row comes back, Hibernate sees that the entity is already managed and returns the
  same instance. You get object identity, but the SQL has already run.

## Hibernate natural IDs fix this, but Spring Data JPA does not use them

A natural ID is a unique business key: an email address, a SKU, an ISBN, a tax identifier. Hibernate has an API for
exactly this case. Mark the field with `@NaturalId`:

```java
@NaturalId
@Column(nullable = false, unique = true)
private String email;
```

and load the entity through the natural ID API:

```java
Session session = entityManager.unwrap(Session.class);
User user = session.find(User.class, email, KeyType.NATURAL);
```

This lookup is cache-aware. Hibernate keeps a map from natural ID to primary key in the persistence context. The first
call runs one query. Every later call with the same email, in the same session, finds the primary key in that map and
returns the managed entity without SQL. With `@NaturalIdCache`, the map is also stored in the second-level cache, so
frequently used keys are resolved without SQL across requests as well.

Spring Data JPA does not use this API. It reads the method name `findByEmail` and builds a JPQL query from it.
`@NaturalId` on the field does not change that. Until now the fix was a hand-written repository fragment, once per
repository (see [Alternatives](#alternatives-compared)).

## What this library does

Add the library to the classpath. The same repository method:

```java
Optional<User> findByEmail(String email);
```

now runs as

```java
session.find(User.class, email, KeyType.NATURAL);
```

on the Hibernate session of the current transaction, with the caching described above.

The map between natural ID and primary key works in both directions. An entity loaded with `findById`, or through an
association, is found by `findByEmail` without a query. An entity loaded with `findByEmail` answers a later `findById`
without a query.

## Installation

Maven:

```xml
<dependency>
    <groupId>com.pocketcombats</groupId>
    <artifactId>spring-data-naturalid</artifactId>
    <version>2.0</version>
    <scope>runtime</scope>
</dependency>
```

Gradle:

```kotlin
runtimeOnly("com.pocketcombats:spring-data-naturalid:2.0")
```

The library registers itself through Spring Boot autoconfiguration. There is nothing to enable and no API to call, so
`runtime` scope is enough. If your application does not load autoconfigurations, import the configuration class
yourself. Your code then refers to a class of the library, so use the default scope (Gradle: `implementation`):

```java
@Import(com.pocketcombats.persistence.NaturalIdQueryMethodBeanPostProcessor.class)
```

## Usage

### 1. Mark the business key with `@NaturalId`

```java
import org.hibernate.annotations.NaturalId;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    private Long id;

    @NaturalId
    @Column(nullable = false, unique = true)
    private String email;

    // ...
}
```

Any unique business key works: an email address, a SKU, an ISBN, a tax identifier, a country code, an id from an
external system. A `@NaturalId` is immutable by default. If the value can change, declare `@NaturalId(mutable = true)`;
Hibernate then checks the session for changed keys before each lookup.

### 2. Keep the repository as it is

```java
public interface UserRepository extends Repository<User, Long> {

    Optional<User> findById(Long id);

    // Executed as a Hibernate natural ID lookup
    Optional<User> findByEmail(String email);

    // The same; returns null when nothing matches
    @Nullable User getByEmail(String email);
}
```

### 3. Nothing else to do

No annotations on the repository, no configuration properties. At startup the library looks at every Spring Data JPA
repository that `@EnableJpaRepositories` or Spring Boot defines. For each repository whose entity has a single
`@NaturalId` attribute, it intercepts the matching methods.

### Which methods are intercepted

A method is intercepted when its name is exactly `findBy<Attribute>` or `getBy<Attribute>` for the natural ID, it
takes one argument, it returns the entity or an `Optional` of it, and Spring Data derives its query from the method
name. The library listens for the queries Spring Data creates and replaces only derived ones.

A method with its own query is left to Spring Data: `@Query`, `@NativeQuery`, `@Procedure`, or a named query called
`User.findByEmail` (declared with `@NamedQuery` or in `META-INF/jpa-named-queries.properties`). So is a method with
query hints (`@QueryHints`, `@Meta`). Examples for an entity `User` with the natural ID `email`:

| Method on `UserRepository`                                             | Intercepted | Why                                                      |
|------------------------------------------------------------------------|-------------|----------------------------------------------------------|
| `Optional<User> findByEmail(String email)`                             | yes         | derived query, one argument, returns the entity          |
| `User findByEmail(String email)`                                       | yes         | the entity itself, without `Optional`, is fine           |
| `@Nullable User getByEmail(String email)`                              | yes         | `getBy` is accepted too; `@Nullable` makes no difference |
| `@Lock(PESSIMISTIC_WRITE) Optional<User> findByEmail(String email)`    | yes         | the lock mode is passed on, see below                    |
| `@EntityGraph(attributePaths = "roles") User getByEmail(String email)` | yes         | the graph is passed on, see below                        |
| `Optional<User> findByEmailIgnoreCase(String email)`                   | no          | the name must be exactly `findByEmail` / `getByEmail`    |
| `List<User> findByEmail(String email)`                                 | no          | returns a collection                                     |
| `UserSummary findByEmail(String email)`                                | no          | returns a projection                                     |
| `Optional<User> findByEmail(String email, Sort sort)`                  | no          | takes more than one argument                             |
| `default User getByEmail(String email) { ... }`                        | no          | default methods run as written                           |
| `@Query("...") Optional<User> findByEmail(String email)`               | no          | declares its own query                                   |
| `@QueryHints(...) Optional<User> findByEmail(String email)`            | no          | hints need a query                                       |

Methods that are not intercepted stay normal Spring Data methods. A `findByEmail` or `getByEmail` query method whose
signature does not match is reported with a warning at startup. Any other method that is left to Spring Data is named
in the DEBUG log with the reason. A default method is not logged: for Spring Data it is not a query method.

A `null` key is not looked up. The call goes to Spring Data's query, which searches for `email is null`.

### Missing entities

A lookup that finds nothing is answered the same way Spring Data answers the query method: an `Optional` is empty, any
other return type is `null`. In a `@NullMarked` package, or in a Kotlin repository, Spring Data's nullability
validation turns that `null` into an `EmptyResultDataAccessException` for a non-null return type, as it does for every
other query method.

### Locks and entity graphs on intercepted methods

`@Lock` and `@EntityGraph` on an intercepted method are passed on to Hibernate's natural ID lookup:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<User> findByEmail(String email);

@EntityGraph(attributePaths = "roles")
User getByEmail(String email);
```

**Lock modes.** When the entity is loaded from the database, the lock is part of the loading statement
(`select ... for update`). When the entity is already in the session, Hibernate upgrades its lock with one small extra
statement, exactly as `EntityManager.find` with a lock mode does. A second locking call in the same transaction runs
no SQL. With Spring Data alone, every `@Lock` call runs the full query again.

**Entity graphs.** Inline graphs declared with `attributePaths`, named graphs (`@EntityGraph("User.withRoles")`) and
Spring Data's fallback name (`User.getByEmail` for an `@EntityGraph` without a name) all work, with fetch or load
semantics as declared. When the entity is loaded from the database, the graph is applied in that one statement, as a
join. When the entity is already in the session, or comes from the second-level cache, Hibernate returns it as it is
and does not load the graph's attributes. This is the same behavior as `EntityManager.find` with a graph, and so the
same as Spring Data's `findById` with `@EntityGraph`. If code in the same transaction may load the entity without the
graph first, initialize the association where you need it, for example with `Hibernate.initialize`.

### Hibernate filters and `@TenantId`

Hibernate adds every enabled filter (`@Filter`) to every query for the entity. A natural ID lookup does not: an entity
found in the session is returned without any filter check, and a lookup that reads the database applies only some
kinds of filters. The library therefore follows two rules:

- While a filter that restricts the entity is enabled on the session, calls of an intercepted method go to Spring
  Data's query, and the filter applies as usual. Filters on other entities do not matter. Once the filter is disabled,
  the lookup is used again.
- An entity with a filter that is always enabled goes through Spring Data's query in every session. This is the case
  for `@FilterDef(autoEnabled = true)` and for the filter Hibernate creates for `@TenantId`. The one exception is a
  session of the root tenant, which sees all tenants: Hibernate disables the tenant filter there, so the lookup is
  used.

### Verify it is working

Turn on SQL logging and the library's logging:

```properties
logging.level.org.hibernate.SQL=DEBUG
# Names every intercepted method, and every method left to Spring Data with the reason
logging.level.com.pocketcombats.persistence=DEBUG
# TRACE also logs every call: answered by a natural ID lookup, or left to Spring Data's query
```

Startup log:

```text
DEBUG .p.NaturalIdRepositoryProxyPostProcessor : UserRepository.findByEmail(String) is executed as a natural id lookup
DEBUG .p.NaturalIdRepositoryProxyPostProcessor : UserRepository.getByEmail(String) is left to Spring Data: its query is not derived from the method name
```

Interception is decided once, at startup. A single call can still go to Spring Data's query (see
[Which methods are intercepted](#which-methods-are-intercepted) and
[Hibernate filters and `@TenantId`](#hibernate-filters-and-tenantid)). At TRACE level every call says which way it
went:

```text
TRACE c.p.p.NaturalIdQueryMethodInterceptor    : Loading com.example.User with natural id foo@example.com
TRACE c.p.p.NaturalIdQueryMethodInterceptor    : com.example.User with natural id foo@example.com is left to Spring Data: an enabled filter restricts the entity
```

Hibernate statistics count natural ID lookups separately from queries:

```properties
spring.jpa.properties.hibernate.generate_statistics=true
```

```java
Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
statistics.getNaturalIdQueryExecutionCount();  // lookups that went to the database
statistics.getNaturalIdCacheHitCount();        // lookups answered by the L2 natural ID cache
```

In a test, assert on the statement count with Hibernate's `StatementInspector`, datasource-proxy's `QueryCountHolder`
or Hypersistence's `SQLStatementCountValidator`. Two `findByEmail` calls in one transaction must produce one statement.

In a `@DataJpaTest` the library is off unless you import it: a test slice loads only its own list of
autoconfigurations, and this library is not on that list. A `@SpringBootTest` loads the library like the application
does.

```java
@DataJpaTest
@ImportAutoConfiguration(NaturalIdQueryMethodBeanPostProcessor.class)
class UserRepositoryTest {
    // ...
}
```

## Caching across requests: second-level cache with `@NaturalIdCache`

Inside one session, the library needs no cache configuration. To avoid SQL for frequently used keys **across**
requests, let Hibernate cache both parts of the lookup in the second-level cache:

```java
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.NaturalIdCache;

@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)   // entity state, keyed by primary key
@NaturalIdCache                                        // email -> primary key
public class User {

    @NaturalId
    private String email;

    // ...
}
```

You need both annotations. `@NaturalIdCache` alone resolves the email to a primary key from the cache, but then loads
the entity from the database. `@Cache` alone is never used for a lookup by email.

The second-level cache needs a provider. Example with JCache and Caffeine, with `org.hibernate.orm:hibernate-jcache`
and `com.github.ben-manes.caffeine:jcache` on the classpath:

```properties
spring.jpa.properties.hibernate.cache.use_second_level_cache=true
spring.jpa.properties.hibernate.cache.region.factory_class=jcache
spring.jpa.properties.hibernate.javax.cache.provider=com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider
```

Ehcache, Infinispan, Hazelcast and Redisson work the same way. The normal second-level cache rules apply: writes made
outside Hibernate (native SQL, other applications, database triggers) are not seen by the cache, and a deployment with
several nodes needs a clustered provider, or must accept that data can be out of date for a short time.

## When it helps, and when it does not

It helps when the same key is looked up more than once by code that shares a persistence context:

- several services in one `@Transactional` call chain resolve the same user, product or account;
- request-scoped code runs with `spring.jpa.open-in-view=true` (the Spring Boot default);
- the same entity is loaded by primary key and by business key in one transaction;
- the same rows are looked up on most requests, and `@NaturalIdCache` is enabled.

It does not help when:

- **every lookup runs in its own session.** With `open-in-view=false` and no surrounding transaction, each call gets a
  new, short-lived session, the same as with Spring Data alone. Only the second-level cache can save SQL in this case.
- **the key does not exist.** Hibernate does not cache a miss. A check like "is this email already taken?" queries the
  database every time.
- **the method is not intercepted, or the call is sent to Spring Data's query.** See
  [Which methods are intercepted](#which-methods-are-intercepted) and
  [Hibernate filters and `@TenantId`](#hibernate-filters-and-tenantid).
- **the repeated queries come from associations.** N+1 selects on `@OneToMany` or lazy `@ManyToOne` are a different
  problem. Use `@EntityGraph`, `JOIN FETCH`, `@BatchSize` or `hibernate.default_batch_fetch_size` for those.

## Alternatives compared

All options below stop `findByEmail` from querying on every call. The first two are what you would write by hand for
each repository. This library does that for you.

### Hand-written repository fragment calling `bySimpleNaturalId` or `Session.find`

```java
public interface UserByEmailLookup {
    Optional<User> findByEmail(String email);
}

class UserByEmailLookupImpl implements UserByEmailLookup {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(em.unwrap(Session.class).find(User.class, email, KeyType.NATURAL));
    }
}

public interface UserRepository extends Repository<User, Long>, UserByEmailLookup {
}
```

The runtime behavior is the same as with this library, and callers do not change. The cost is two extra types per
repository, and the next derived `findBySku` in another repository still runs a query on every call.

### Custom repository base class with `findByNaturalId`

With `@EnableJpaRepositories(repositoryBaseClass = ...)` one generic `findByNaturalId(Object key)` covers every
entity. Callers must change from `findByEmail(email)` to `findByNaturalId(email)`, and lose the readable method name
and the compile-time type of the argument.

### Hibernate query cache on the derived query

```properties
spring.jpa.properties.hibernate.cache.use_query_cache=true
```

```java
@QueryHints(@QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_CACHEABLE, value = "true"))
Optional<User> findByEmail(String email);
```

The query cache stores the primary keys of the result, per query string and parameters. The entity itself comes from
the second-level cache, so it still needs `@Cache`. Any insert, update or delete on the `users` table invalidates every
cached query on that table; updating a `last_login_at` column on each login is enough to keep the cache empty. It fits
reference tables that rarely change. Unlike a natural ID lookup, it does cache empty results.

### Spring Cache `@Cacheable` on the repository or service method

```java
@Cacheable("usersByEmail")
Optional<User> findByEmail(String email);
```

This caches entity instances outside Hibernate. Across requests you get detached objects: lazy associations throw
`LazyInitializationException`, changes are not tracked, and the cached instance is not the one the current session
manages. Nothing removes the entry when the user is updated, unless you add `@CacheEvict` to every method that writes
the entity. If you want an application-level cache, cache the mapping from email to id (or a DTO), then call
`findById`, which does use the persistence context and the second-level cache.

### Load once, then pass the entity (or its id) down

Load the user once at the start of the request and pass it, or only its primary key, to every service. This needs no
extra setup, but method signatures change across the code base (or you add a `@RequestScope` holder), it covers only
the entity you pass, and it does nothing across requests.

### Summary

| Approach                                | Same key, same transaction              | Frequently used rows across requests | Stays correct after writes             | What you change                                  |
|-----------------------------------------|-----------------------------------------|--------------------------------------|----------------------------------------|--------------------------------------------------|
| **This library**                        | no SQL (session cache)                  | with `@NaturalIdCache` + `@Cache`    | yes, for writes made through Hibernate | one dependency, `@NaturalId` on the field        |
| Hand-written fragment                   | no SQL                                  | with `@NaturalIdCache` + `@Cache`    | yes                                    | fragment interface + `Impl` per repository       |
| Custom base class `findByNaturalId`     | no SQL                                  | with `@NaturalIdCache` + `@Cache`    | yes                                    | base class; every caller switches method         |
| Hibernate query cache                   | no SQL while the table is unchanged     | while the table is unchanged         | any write to the table clears it       | config + hint per method, `@Cache` on the entity |
| Spring `@Cacheable` on the method       | detached copies, not the managed entity | yes                                  | manual `@CacheEvict` everywhere        | cache config + annotations + eviction            |
| Pass the entity / id down               | no SQL                                  | only via `findById` + `@Cache`       | yes                                    | method signatures or a request-scoped holder     |

## Compatibility

Java 17 or newer is required.

Hibernate 7.3 or newer is required, which Spring Boot 4.1 manages by default. The lookup uses `Session.find` with
`KeyType.NATURAL`, which Hibernate added in 7.3.

Kotlin repositories work. Declare the return type as `User?` or `Optional<User>` to get `null` or an empty `Optional`
on a miss; a non-null `User` throws `EmptyResultDataAccessException` (see [Missing entities](#missing-entities)).

Several persistence units are supported. Each repository uses the `EntityManager` that Spring Data configured it with
(`@EnableJpaRepositories(entityManagerFactoryRef = ...)`), so its lookups go to its own persistence unit, also when
two units map the same entity.

## How it works

No reflection on your entities, no bytecode manipulation.

1. A Spring Boot autoconfiguration registers a `BeanPostProcessor`. It adds a `RepositoryProxyPostProcessor` to the
   repository factory of every Spring Data JPA repository that `@EnableJpaRepositories` or Spring Boot defines.
   Repositories of other Spring Data modules are not touched.
2. For each repository, the post-processor asks Hibernate's mapping metamodel, in the repository's own persistence
   unit, whether the domain type has a natural ID. Entities without one, or with a composite one, are skipped.
3. It collects the query methods that match [Which methods are intercepted](#which-methods-are-intercepted), with
   their `@Lock` mode and `@EntityGraph`, read once at startup. When Spring Data creates the repository's queries, the
   library keeps only the collected methods whose query is derived from the method name.
4. These methods, and only those, get an AOP interceptor. It calls `Session.find(..., key, KeyType.NATURAL)`,
   with the lock mode and the graph if declared, on the `EntityManager` bound to the current transaction (or to the Open
   Session in View request). When there is no such `EntityManager`, it uses a new short-lived one, as Spring Data does
   for the query.
5. Before each lookup the interceptor checks that the lookup would answer as the query would. A `null` key, or an
   enabled Hibernate filter on the entity, sends the call on to Spring Data's query.

A repository the library cannot handle keeps its normal Spring Data behavior. A composite natural ID is logged as a
warning. A repository whose persistence unit the library cannot find, or whose persistence unit is not Hibernate's, is
named in the DEBUG log.

## Limitations

1. Entities with exactly one `@NaturalId` attribute. Composite natural IDs are not supported; a warning is logged at
   startup.
2. Only methods that match [Which methods are intercepted](#which-methods-are-intercepted) are affected, and a call
   for an entity that an enabled Hibernate filter restricts (`@TenantId` entities included) still goes to Spring Data's
   query.
3. An `@EntityGraph` is applied only when the entity is loaded from the database
   (see [Locks and entity graphs](#locks-and-entity-graphs-on-intercepted-methods)).
4. A natural ID lookup answers like `findById`: with the entity the session already holds, without asking the
   database again. A row that was deleted, or whose natural ID was changed, by a bulk `@Modifying` query or native SQL
   is still found until the session is cleared. Use `clearAutomatically = true` on such queries.
5. Repositories you build yourself are not intercepted: a `JpaRepositoryFactoryBean` declared as a `@Bean`, or
   `new JpaRepositoryFactory(entityManager).getRepository(...)`. The library reads a repository's persistence unit
   from the bean definition that `@EnableJpaRepositories` creates, and these repositories have none.
6. In an AOT-processed application (`spring.aot.enabled=true`, or a native image), Spring Data generates the code of
   `findByEmail` at build time, and a generated method is not intercepted. Run AOT processing with
   `spring.aot.repositories.enabled=false` to keep the interception.

## FAQ

### Does Spring Data JPA use the Hibernate second-level cache for `findBy...` query methods?

No. The second-level entity cache is keyed by primary key, so a `where email = ?` query cannot be answered from it.
Query methods reach the second-level cache only through the query cache (with its table-wide invalidation), or, with
this library, through Hibernate's natural ID cache.

### I added `@NaturalId`, but `findByEmail` still hits the database

`@NaturalId` is a Hibernate annotation. Spring Data does not read it and still builds a JPQL query from the method
name. Something has to call `Session.find` with `KeyType.NATURAL` instead: this library, or one of the
[alternatives](#alternatives-compared).

### How do I keep one method as a normal Spring Data query?

Give the method a query of its own, for example with `@Query`, or add `@QueryHints`. The library leaves such methods
to Spring Data (see [Which methods are intercepted](#which-methods-are-intercepted)).

### My database collation is case-insensitive. Does that matter?

Cache keys are compared exactly, and the key Hibernate remembers is the value stored in the entity. If the row holds
`foo@example.com`, a lookup of `Foo@example.com` finds the same row in SQL and returns the same entity, but it runs a
query on every call. Only a lookup with the stored value is answered from the cache. The results stay correct.
Normalize the key before the lookup, and before you store it, to get cache hits.

## License

Apache License 2.0. See [LICENSE](LICENSE).
