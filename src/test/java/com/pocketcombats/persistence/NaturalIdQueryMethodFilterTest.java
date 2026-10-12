package com.pocketcombats.persistence;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static com.pocketcombats.persistence.TestApplication.statementInspector;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * While an enabled Hibernate filter restricts the entity, the call is left to Spring Data's query.
 */
@DataJpaTest
public class NaturalIdQueryMethodFilterTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    AccountRepository repository;

    @Autowired
    CompanyRepository companyRepository;

    @BeforeEach
    public void setUp() {
        statementInspector.reset();
    }

    @Test
    public void filterEnabledAfterTheLoadBypassesTheSessionCache() {
        repository.findByIban("iban1").orElseThrow();
        repository.findByIban("iban1").orElseThrow();
        assertThat(statementInspector.getStatements())
                .as("fixture precondition: statements for two findByIban calls without a filter")
                .hasSize(1);

        session().enableFilter("tenant").setParameter("tenant", "other");

        assertThat(repository.findByIban("iban1")).isEmpty();
    }

    @Test
    public void filterOfAnotherEntityKeepsTheSessionCache() {
        session().enableFilter("tenant").setParameter("tenant", "other");

        companyRepository.findByTaxIdentifier("taxId").orElseThrow();
        companyRepository.findByTaxIdentifier("taxId").orElseThrow();
        assertThat(statementInspector.getStatements()).hasSize(1);
    }

    private Session session() {
        return entityManager.unwrap(Session.class);
    }
}
