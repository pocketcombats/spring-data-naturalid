package com.pocketcombats.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * Named entity graphs declared on {@link Company}.
 */
public interface NamedGraphCompanyRepository extends Repository<Company, Integer> {

    @EntityGraph("Company.withEmployees")
    Optional<Company> findByTaxIdentifier(String taxIdentifier);

    /**
     * Unnamed: Spring Data falls back to the graph {@code Company.getByTaxIdentifier}.
     */
    @EntityGraph
    Company getByTaxIdentifier(String taxIdentifier);
}
