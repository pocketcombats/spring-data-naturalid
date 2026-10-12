package com.pocketcombats.persistence;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static com.pocketcombats.persistence.TestApplication.statementInspector;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

@DataJpaTest
public class NaturalIdQueryMethodEntityGraphTest {

    @Autowired
    FetchGraphCompanyRepository fetchGraphRepository;

    @Autowired
    NamedGraphCompanyRepository namedGraphRepository;

    @BeforeEach
    public void setUp() {
        statementInspector.reset();
    }

    @Test
    public void fetchGraphIsAppliedInTheLoadingStatement() {
        Company companyA = fetchGraphRepository.findByTaxIdentifier("taxId").orElseThrow();
        Company companyB = fetchGraphRepository.findByTaxIdentifier("taxId").orElseThrow();
        assertThat(companyB).isSameAs(companyA);

        assertGraphLoadedInOneStatement(companyA);
    }

    @Test
    public void namedGraphIsAppliedInTheLoadingStatement() {
        Company company = namedGraphRepository.findByTaxIdentifier("taxId").orElseThrow();

        assertGraphLoadedInOneStatement(company);
    }

    @Test
    public void graphNamedAfterTheMethodIsAppliedInTheLoadingStatement() {
        Company company = namedGraphRepository.getByTaxIdentifier("taxId");

        assertGraphLoadedInOneStatement(company);
    }

    private void assertGraphLoadedInOneStatement(Company company) {
        assertThat(statementInspector.getStatements())
                .singleElement(as(STRING))
                .containsIgnoringCase("join");
        assertThat(Hibernate.isInitialized(company.getEmployees())).isTrue();
    }
}
