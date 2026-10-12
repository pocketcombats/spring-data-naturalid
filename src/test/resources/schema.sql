CREATE TABLE company
(
    id              INTEGER NOT NULL,
    tax_identifier  VARCHAR,
    unique_property VARCHAR NOT NULL UNIQUE,
    PRIMARY KEY (id),
    UNIQUE (tax_identifier)
);

CREATE TABLE employee
(
    id         INTEGER NOT NULL,
    badge      VARCHAR NOT NULL UNIQUE,
    company_id INTEGER NOT NULL REFERENCES company (id),
    PRIMARY KEY (id)
);

CREATE TABLE account
(
    id     INTEGER NOT NULL,
    iban   VARCHAR NOT NULL UNIQUE,
    tenant VARCHAR NOT NULL,
    PRIMARY KEY (id)
);
