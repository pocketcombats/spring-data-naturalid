package com.pocketcombats.persistence;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * A base interface whose query method is inherited, with a type variable for the entity.
 */
@NoRepositoryBean
public interface TaxIdentifierRepository<T> extends Repository<T, Integer> {

    Optional<T> findByTaxIdentifier(String taxIdentifier);
}
