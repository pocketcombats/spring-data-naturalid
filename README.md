# Natural IDs
Natural ids represent unique identifiers that naturally exist within your domain model.
Even if a natural id does not make a good primary key, it still is useful to tell Hibernate about it.  

Let's consider the following entity:
```java
@Entity
public class Company {
    
	@Id
	private Integer id;
    
	@NaturalId
	private String taxIdentifier;
	...
}
```
When using Spring Data JPA methods such as `getByTaxIdentifier`, Spring Data will force Hibernate to perform a query ignoring the session cache (and L2 cache).  
This library detects such methods and invokes Hibernate's `Session.bySimpleNaturalId()` instead of the default Spring Data implementation.  

If you want to enable L2 cache, don't forget to annotate your model with `@NaturalIdCache`.  

# Installation
Add the following dependency to your project:  
```xml
    <dependency>
        <groupId>com.github.pocketcombats</groupId>
        <artifactId>spring-data-naturalid</artifactId>
        <version>v1.0</version>
        <scope>runtime</scope>
    </dependency>
```
