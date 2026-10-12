package com.pocketcombats.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.OneToMany;
import org.hibernate.annotations.NaturalId;

import java.util.ArrayList;
import java.util.List;

@Entity
@NamedEntityGraph(name = "Company.withEmployees", attributeNodes = @NamedAttributeNode("employees"))
// Spring Data's fallback name for an unnamed @EntityGraph on getByTaxIdentifier
@NamedEntityGraph(name = "Company.getByTaxIdentifier", attributeNodes = @NamedAttributeNode("employees"))
public class Company {

    @Id
    private Integer id;

    @NaturalId
    private String taxIdentifier;

    @Column(nullable = false, updatable = false, unique = true)
    private String uniqueProperty;

    @OneToMany(mappedBy = "company", fetch = FetchType.LAZY)
    private List<Employee> employees = new ArrayList<>();

    public Integer getId() {
        return id;
    }

    public String getTaxIdentifier() {
        return taxIdentifier;
    }

    public String getUniqueProperty() {
        return uniqueProperty;
    }

    public List<Employee> getEmployees() {
        return employees;
    }
}
