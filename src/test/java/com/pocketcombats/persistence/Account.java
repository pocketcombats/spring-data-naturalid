package com.pocketcombats.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.ParamDef;

/**
 * Restricted to a tenant once a session enables its filter.
 */
@Entity
@FilterDef(name = "tenant", parameters = @ParamDef(name = "tenant", type = String.class))
@Filter(name = "tenant", condition = "tenant = :tenant")
public class Account {

    @Id
    private Integer id;

    @NaturalId
    private String iban;

    private String tenant;
}
