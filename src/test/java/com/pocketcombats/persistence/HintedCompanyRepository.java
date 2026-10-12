package com.pocketcombats.persistence;

import jakarta.persistence.QueryHint;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface HintedCompanyRepository extends Repository<Company, Integer> {

    @QueryHints(@QueryHint(name = HibernateHints.HINT_READ_ONLY, value = "true"))
    Optional<Company> findByTaxIdentifier(String taxIdentifier);
}
