package com.pocketcombats.persistence;

import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface BranchRepository extends Repository<Branch, Integer> {

    Optional<Branch> findByCompanyAndCode(Company company, String code);

    /**
     * Attributes in the different order.
     */
    Optional<Branch> findByCodeAndCompany(String code, Company company);
}
