package com.pocketcombats.persistence;

import org.springframework.data.domain.Sort;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * Natural Id method names of shapes a natural id lookup does not answer, so they are not intercepted.
 */
public interface ShapeCompanyRepository extends Repository<Company, Integer> {

    interface TaxIdentifierOnly {

        String getTaxIdentifier();
    }

    Optional<Company> findByTaxIdentifier(String taxIdentifier, Sort sort);

    Optional<TaxIdentifierOnly> findByTaxIdentifier(String taxIdentifier);
}
