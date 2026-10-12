package com.pocketcombats.persistence;

import org.jspecify.annotations.Nullable;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface CompanyRepository extends Repository<Company, Integer> {

    Optional<Company> findByUniqueProperty(String value);

    /**
     * {@code @Nullable}, or the {@code @NullMarked} package would have Spring Data reject a {@code null} argument.
     */
    Optional<Company> findByTaxIdentifier(@Nullable String taxIdentifier);

    /**
     * Non-null by the {@code @NullMarked} package, so a miss is an {@code EmptyResultDataAccessException}.
     */
    Company getByTaxIdentifier(String taxIdentifier);
}
