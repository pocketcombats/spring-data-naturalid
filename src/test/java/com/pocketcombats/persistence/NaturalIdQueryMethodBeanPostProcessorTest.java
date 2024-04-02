package com.pocketcombats.persistence;

import org.hibernate.cfg.JdbcSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate"
})
public class NaturalIdQueryMethodBeanPostProcessorTest {

    private static final CountingStatementInspector countingStatementInspector = new CountingStatementInspector();

    @Autowired
    CompanyRepository repository;

    @BeforeEach
    public void resetCounter() {
        countingStatementInspector.reset();
    }

    @Test
    public void testNaturalIdQueriesCount() {
        Optional<Company> companyA = repository.findByTaxIdentifier("taxId");
        Optional<Company> companyB = repository.findByTaxIdentifier("taxId");
        assumeThat(companyA).isNotEmpty().isEqualTo(companyB);
        assertEquals(1, countingStatementInspector.getCount(), "Expected exactly 1 query");
    }

    @Test
    public void testUniquePropertiesQueriesCount() {
        Optional<Company> companyA = repository.findByUniqueProperty("uniqueValue");
        Optional<Company> companyB = repository.findByUniqueProperty("uniqueValue");
        assumeThat(companyA).isNotEmpty().isEqualTo(companyB);
        assertEquals(
                2, countingStatementInspector.getCount(),
                "Expected findByUniqueProperty to perform query every time"
        );
    }

    @SpringBootApplication
    @Import(NaturalIdQueryMethodBeanPostProcessor.class)
    static class Configuration implements HibernatePropertiesCustomizer {

        @Override
        public void customize(Map<String, Object> hibernateProperties) {
            hibernateProperties.put(JdbcSettings.STATEMENT_INSPECTOR, countingStatementInspector);
        }
    }
}
