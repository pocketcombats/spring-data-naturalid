package com.pocketcombats.persistence;

import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface AccountRepository extends Repository<Account, Integer> {

    Optional<Account> findByIban(String iban);
}
