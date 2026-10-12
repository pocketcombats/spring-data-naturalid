package com.pocketcombats.persistence;

import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface DefaultMethodCompanyRepository extends Repository<Company, Integer> {

    Optional<Company> findByTaxIdentifier(String taxIdentifier);

    // Named after the natural id, but a default method is not a query method
    default Company getByTaxIdentifier(String taxIdentifier) {
        return findByTaxIdentifier(taxIdentifier).orElseThrow();
    }
}
