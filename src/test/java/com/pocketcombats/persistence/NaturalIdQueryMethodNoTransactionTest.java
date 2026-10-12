package com.pocketcombats.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static com.pocketcombats.persistence.TestApplication.statementInspector;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lookups outside a transaction run in a session of their own, unless one is bound to the thread as
 * open-in-view binds one.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class NaturalIdQueryMethodNoTransactionTest {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    CompanyRepository repository;

    @BeforeEach
    public void setUp() {
        statementInspector.reset();
        statistics().setStatisticsEnabled(true);
        statistics().clear();
    }

    @AfterEach
    public void disableStatistics() {
        statistics().setStatisticsEnabled(false);
    }

    @Test
    public void eachLookupLoadsInASessionOfItsOwn() {
        Company companyA = repository.findByTaxIdentifier("taxId").orElseThrow();
        Company companyB = repository.findByTaxIdentifier("taxId").orElseThrow();
        assertThat(companyB)
                .isNotSameAs(companyA)
                .extracting(Company::getId, Company::getTaxIdentifier)
                .containsExactly(companyA.getId(), "taxId");

        assertThat(statementInspector.getStatements()).hasSize(2);
        assertThat(statistics().getNaturalIdQueryExecutionCount())
                .as("natural id loads for two findByTaxIdentifier calls; both are intercepted")
                .isEqualTo(2);
        assertThat(statistics().getSessionOpenCount()).isEqualTo(2);
        assertThat(statistics().getSessionCloseCount()).isEqualTo(2);
    }

    @Test
    public void lookupsShareASessionBoundToTheThread() {
        inASessionBoundToTheThread(() -> {
            Company companyA = repository.findByTaxIdentifier("taxId").orElseThrow();
            Company companyB = repository.findByTaxIdentifier("taxId").orElseThrow();
            assertThat(companyB).isSameAs(companyA);
        });

        assertThat(statementInspector.getStatements()).hasSize(1);
        assertThat(statistics().getNaturalIdQueryExecutionCount())
                .as("natural id loads for two findByTaxIdentifier calls in a session bound to the thread: the "
                        + "first call")
                .isEqualTo(1);
        assertThat(statistics().getSessionOpenCount())
                .as("sessions opened: the bound one, and none by the two findByTaxIdentifier calls")
                .isEqualTo(1);
    }

    /**
     * Runs the action with a session bound to the thread, as open-in-view binds one, but no transaction.
     */
    private void inASessionBoundToTheThread(Runnable action) {
        EntityManager boundEntityManager = entityManagerFactory.createEntityManager();
        TransactionSynchronizationManager.bindResource(
                entityManagerFactory,
                new EntityManagerHolder(boundEntityManager)
        );
        try {
            action.run();
        } finally {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            boundEntityManager.close();
        }
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
