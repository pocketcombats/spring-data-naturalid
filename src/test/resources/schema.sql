CREATE TABLE company
(
    id              INTEGER NOT NULL,
    tax_identifier  VARCHAR,
    unique_property VARCHAR NOT NULL UNIQUE,
    PRIMARY KEY (id),
    UNIQUE (tax_identifier)
);
