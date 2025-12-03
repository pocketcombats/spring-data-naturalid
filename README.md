# Spring Data Natural ID

Enhance Spring Data JPA with Hibernate's Natural ID support for optimized entity lookups.

## What are Natural IDs?

Natural IDs represent unique identifiers that naturally exist within your domain model. Unlike surrogate primary keys (typically generated IDs), natural IDs have business meaning.

**Examples of Natural IDs:**
- Tax identifier for a company
- ISBN for a book
- Email address for a user
- Social security number for a person

## The Problem

When using standard Spring Data JPA methods such as `findByTaxIdentifier` or `getByTaxIdentifier`, Spring Data will force Hibernate to perform a SQL query every time, ignoring Hibernate's session cache (and L2 cache).

This means that even if you've already loaded an entity by its natural ID, Spring Data will execute a new SQL query when you try to load it again.

## The Solution

This library automatically detects repository methods that query entities by their natural identifiers and redirects them to use Hibernate's `Session.bySimpleNaturalId()` method, which:

1. Checks the session cache first
2. Uses optimized queries when going to the database
3. Can leverage L2 cache if configured

## Installation

Add the following dependency to your project:

```xml
<dependency>
    <groupId>com.pocketcombats</groupId>
    <artifactId>spring-data-naturalid</artifactId>
    <version>1.4</version>
    <scope>runtime</scope>
</dependency>
```

## Usage

### 1. Annotate your entity with @NaturalId

```java
@Entity
public class Company {

    @Id
    private Integer id;

    @NaturalId
    private String taxIdentifier;

    // Other fields, getters, setters...
}
```

### 2. Create a repository with methods to find by the natural ID

```java
public interface CompanyRepository extends Repository<Company, Integer> {

    // Standard method to find by primary key
    Optional<Company> findById(Integer id);

    // Method to find by natural ID - will be optimized!
    Optional<Company> findByTaxIdentifier(String taxIdentifier);

    // Alternative semantic - also works
    Company getByTaxIdentifier(String taxIdentifier);
}
```

### 3. That's it!

The library will automatically detect your natural ID methods and optimize them. No additional configuration is needed.

## Advanced: Enabling L2 Cache

If you want to enable L2 cache for natural IDs, annotate your entity with `@NaturalIdCache`:

```java
@Entity
@NaturalIdCache
public class Company {

    @Id
    private Integer id;

    @NaturalId
    private String taxIdentifier;

    // ...
}
```

## Limitations

1. Currently only supports entities with a single natural ID attribute
2. Only works with simple natural IDs (not composite natural IDs)
3. Only intercepts methods named `findByX` or `getByX` where X is the natural ID attribute name

## How It Works

This library uses Spring's `BeanPostProcessor` to enhance repository beans. It:

1. Detects repositories working with entities that have natural IDs
2. Identifies methods that should use natural ID lookups (findByX, getByX)
3. Intercepts these methods and redirects them to use Hibernate's natural ID API

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.
