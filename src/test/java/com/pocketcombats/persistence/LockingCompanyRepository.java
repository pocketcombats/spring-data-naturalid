package com.pocketcombats.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface LockingCompanyRepository extends Repository<Company, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Company> findByTaxIdentifier(String taxIdentifier);
}
