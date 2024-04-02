package com.pocketcombats.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.annotations.NaturalId;

@Entity
public class Company {

    @Id
    private Integer id;

    @NaturalId
    private String taxIdentifier;

    @Column(nullable = false, updatable = false, unique = true)
    private String uniqueProperty;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTaxIdentifier() {
        return taxIdentifier;
    }

    public void setTaxIdentifier(String taxIdentifier) {
        this.taxIdentifier = taxIdentifier;
    }

    public String getUniqueProperty() {
        return uniqueProperty;
    }

    public void setUniqueProperty(String uniqueProperty) {
        this.uniqueProperty = uniqueProperty;
    }
}
