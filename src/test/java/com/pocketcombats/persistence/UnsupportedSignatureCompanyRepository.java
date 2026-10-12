package com.pocketcombats.persistence;

import org.springframework.data.domain.Sort;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * Natural id method names with signatures a natural id lookup does not answer, so they are not intercepted.
 */
public interface UnsupportedSignatureCompanyRepository extends Repository<Company, Integer> {

    interface TaxIdentifierOnly {

        String getTaxIdentifier();
    }

    Optional<Company> findByTaxIdentifier(String taxIdentifier, Sort sort);

    Optional<TaxIdentifierOnly> findByTaxIdentifier(String taxIdentifier);
}
