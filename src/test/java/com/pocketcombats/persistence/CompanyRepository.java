package com.pocketcombats.persistence;

import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface CompanyRepository extends Repository<Company, Integer> {

    Optional<Company> findById(Integer id);

    Optional<Company> findByUniqueProperty(String value);

    Optional<Company> findByTaxIdentifier(String taxIdentifier);
}
