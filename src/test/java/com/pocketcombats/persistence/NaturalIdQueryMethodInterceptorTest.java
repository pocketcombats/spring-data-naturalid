package com.pocketcombats.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.EmptyResultDataAccessException;

import java.util.Optional;

import static com.pocketcombats.persistence.TestApplication.statementInspector;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest
public class NaturalIdQueryMethodInterceptorTest {

    private static final String MISSING = "missing";

    @Autowired
    EntityManager entityManager;

    @Autowired
    CompanyRepository repository;

    @Test
    public void optionalReturnIsEmptyOnMiss() {
        assertThat(repository.findByTaxIdentifier(MISSING)).isEmpty();
    }

    @Test
    public void nonNullReturnThrowsOnMiss() {
        // Thrown by Spring Data's nullability validation; the interceptor answers null
        assertThatExceptionOfType(EmptyResultDataAccessException.class)
                .isThrownBy(() -> repository.getByTaxIdentifier(MISSING));
    }

    @Test
    public void nullArgumentIsLeftToSpringData() {
        // Found by Spring Data's "is null" query, which a natural id lookup cannot run
        entityManager.createNativeQuery(
                "INSERT INTO company (id, tax_identifier, unique_property) VALUES (2, NULL, 'uniqueValue2')"
        ).executeUpdate();
        statementInspector.reset();

        Optional<Company> companyA = repository.findByTaxIdentifier(null);
        Optional<Company> companyB = repository.findByTaxIdentifier(null);
        assertThat(companyA).map(Company::getId).contains(2);
        assertThat(companyB).isEqualTo(companyA);
        assertThat(statementInspector.getStatements()).hasSize(2);
    }
}
