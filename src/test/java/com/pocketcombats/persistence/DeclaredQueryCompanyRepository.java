package com.pocketcombats.persistence;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * A natural id method name whose query is declared, so it is not intercepted.
 */
public interface DeclaredQueryCompanyRepository extends Repository<Company, Integer> {

    @Query("select c from Company c where c.taxIdentifier = ?1")
    Optional<Company> findByTaxIdentifier(String taxIdentifier);
}
