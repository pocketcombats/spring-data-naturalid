package com.pocketcombats.persistence;

import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface EmployeeRepository extends Repository<Employee, Integer> {

    /**
     * Answered by the named query {@code Employee.findByBadge} declared on the entity, not intercepted.
     */
    Optional<Employee> findByBadge(String badge);
}
