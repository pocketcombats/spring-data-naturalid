package com.pocketcombats.persistence;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Optional;

import static com.pocketcombats.persistence.TestApplication.statementInspector;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A repository of a second persistence unit that manages {@link Company} as the first one does. The unit is
 * declared as Spring Boot documents it, not a default candidate, so a lookup by entity type would find the first
 * unit. The repository must use its own.
 */
@DataJpaTest
@Import(NaturalIdQueryMethodSecondPersistenceUnitTest.SecondPersistenceUnit.class)
@Transactional("secondTransactionManager")
public class NaturalIdQueryMethodSecondPersistenceUnitTest {

    public interface SecondUnitCompanyRepository extends Repository<Company, Integer> {

        Optional<Company> findByTaxIdentifier(String taxIdentifier);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableJpaRepositories(
            basePackageClasses = SecondUnitCompanyRepository.class,
            considerNestedRepositories = true,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = SecondUnitCompanyRepository.class
            ),
            entityManagerFactoryRef = "secondEntityManagerFactory",
            transactionManagerRef = "secondTransactionManager"
    )
    static class SecondPersistenceUnit {

        @Bean(defaultCandidate = false)
        @Qualifier("second")
        EmbeddedDatabase secondDataSource() {
            EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .generateUniqueName(true)
                    .addScript("schema.sql")
                    .build();
            // The tax identifier of the company in the first database, with a unique property of its own
            new JdbcTemplate(database).update(
                    "INSERT INTO company (id, tax_identifier, unique_property) VALUES (1, 'taxId', 'second database')"
            );
            return database;
        }

        @Bean(defaultCandidate = false)
        @Qualifier("second")
        LocalContainerEntityManagerFactoryBean secondEntityManagerFactory(
                EntityManagerFactoryBuilder builder,
                @Qualifier("second") DataSource dataSource
        ) {
            return builder.dataSource(dataSource).packages(Company.class).persistenceUnit("second").build();
        }

        @Bean(defaultCandidate = false)
        @Qualifier("second")
        JpaTransactionManager secondTransactionManager(
                @Qualifier("second") EntityManagerFactory entityManagerFactory
        ) {
            return new JpaTransactionManager(entityManagerFactory);
        }
    }

    @Autowired
    SecondUnitCompanyRepository repository;

    @BeforeEach
    public void setUp() {
        statementInspector.reset();
    }

    @Test
    public void naturalIdLookupIsAnsweredByTheRepositorysPersistenceUnit() {
        Company company = repository.findByTaxIdentifier("taxId").orElseThrow();
        repository.findByTaxIdentifier("taxId").orElseThrow();
        assertThat(company.getUniqueProperty())
                .as("unique property of the company found; the first database has 'uniqueValue' for it")
                .isEqualTo("second database");
        assertThat(statementInspector.getStatements()).hasSize(1);
    }
}
