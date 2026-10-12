package com.pocketcombats.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;
import org.hibernate.annotations.NaturalId;

@Entity
// Preferred by Spring Data to a derived query for EmployeeRepository.findByBadge
@NamedQuery(name = "Employee.findByBadge", query = "select e from Employee e where e.badge = ?1")
public class Employee {

    @Id
    private Integer id;

    @NaturalId
    private String badge;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id")
    private Company company;
}
