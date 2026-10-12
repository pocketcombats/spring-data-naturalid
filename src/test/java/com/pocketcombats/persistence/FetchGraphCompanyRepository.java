package com.pocketcombats.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface FetchGraphCompanyRepository extends Repository<Company, Integer> {

    @EntityGraph(attributePaths = "employees")
    Optional<Company> findByTaxIdentifier(String taxIdentifier);
}
