package com.pocketcombats.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.hibernate.annotations.NaturalId;

/**
 * Composite natural id: its company and a code unique within the company.
 */
@Entity
public class Branch {

    @Id
    private Integer id;

    @NaturalId
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @NaturalId
    private String code;
}
