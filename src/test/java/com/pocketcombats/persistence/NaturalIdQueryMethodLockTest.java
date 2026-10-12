package com.pocketcombats.persistence;

import jakarta.persistence.EntityManager;
import org.hibernate.LockMode;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static com.pocketcombats.persistence.TestApplication.statementInspector;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

@DataJpaTest
public class NaturalIdQueryMethodLockTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    LockingCompanyRepository lockingRepository;

    @BeforeEach
    public void setUp() {
        statementInspector.reset();
    }

    @Test
    public void lockIsAcquiredOnceForRepeatedCalls() {
        Company companyA = lockingRepository.findByTaxIdentifier("taxId").orElseThrow();
        Company companyB = lockingRepository.findByTaxIdentifier("taxId").orElseThrow();
        assertThat(companyB).isSameAs(companyA);

        assertThat(statementInspector.getStatements())
                .singleElement(as(STRING))
                .containsIgnoringCase("for update");
        assertThat(entityManager.unwrap(Session.class).getCurrentLockMode(companyA))
                .isEqualTo(LockMode.PESSIMISTIC_WRITE);
    }
}
