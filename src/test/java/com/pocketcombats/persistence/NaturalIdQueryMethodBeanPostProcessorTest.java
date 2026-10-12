package com.pocketcombats.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Sort;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;

import static com.pocketcombats.persistence.TestApplication.statementInspector;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest
public class NaturalIdQueryMethodBeanPostProcessorTest {

    @Autowired
    CompanyRepository repository;

    @Autowired
    DeclaredQueryCompanyRepository declaredQueryRepository;

    @Autowired
    HintedCompanyRepository hintedRepository;

    @Autowired
    DefaultMethodCompanyRepository defaultMethodRepository;

    @Autowired
    EmployeeRepository employeeRepository;

    @Autowired
    InheritedMethodCompanyRepository inheritedMethodRepository;

    @Autowired
    UnsupportedSignatureCompanyRepository unsupportedSignatureRepository;

    @BeforeEach
    public void setUp() {
        statementInspector.reset();
    }

    @Test
    public void naturalIdLookupIsAnsweredFromTheSessionCache() {
        assertStatementsForTwoCalls(() -> repository.findByTaxIdentifier("taxId"), 1);
    }

    @Test
    public void inheritedLookupIsAnsweredFromTheSessionCache() {
        assertStatementsForTwoCalls(() -> inheritedMethodRepository.findByTaxIdentifier("taxId"), 1);
    }

    @Test
    public void uniqueColumnLookupIsLeftToSpringData() {
        assertStatementsForTwoCalls(() -> repository.findByUniqueProperty("uniqueValue"), 2);
    }

    @Test
    public void declaredQueryIsLeftToSpringData() {
        assertStatementsForTwoCalls(() -> declaredQueryRepository.findByTaxIdentifier("taxId"), 2);
    }

    @Test
    public void namedQueryIsLeftToSpringData() {
        assertStatementsForTwoCalls(() -> employeeRepository.findByBadge("badge1"), 2);
    }

    @Test
    public void queryHintsAreLeftToSpringData() {
        assertStatementsForTwoCalls(() -> hintedRepository.findByTaxIdentifier("taxId"), 2);
    }

    @Test
    public void sortParameterIsLeftToSpringData() {
        assertStatementsForTwoCalls(() -> unsupportedSignatureRepository.findByTaxIdentifier("taxId", Sort.unsorted()), 2);
    }

    @Test
    public void defaultMethodIsLeftToSpringData() {
        // Thrown by the default method's body; a natural id lookup would fail the nullability validation instead
        assertThatExceptionOfType(NoSuchElementException.class)
                .isThrownBy(() -> defaultMethodRepository.getByTaxIdentifier("missing"));
    }

    @Test
    public void projectionIsLeftToSpringData() {
        UnsupportedSignatureCompanyRepository.TaxIdentifierOnly projection =
                assertStatementsForTwoCalls(() -> unsupportedSignatureRepository.findByTaxIdentifier("taxId"), 2);
        assertThat(projection.getTaxIdentifier()).isEqualTo("taxId");
    }

    private static <T> T assertStatementsForTwoCalls(Supplier<Optional<T>> lookup, int expectedStatements) {
        // Calls the lookup twice, which must find the seeded entity both times; returns result of the first call.
        T found = lookup.get().orElseThrow();
        lookup.get().orElseThrow();
        assertThat(statementInspector.getStatements()).hasSize(expectedStatements);
        return found;
    }
}
