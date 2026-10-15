INSERT INTO company (id, tax_identifier, unique_property) VALUES (1, 'taxId', 'uniqueValue');
INSERT INTO employee (id, badge, company_id) VALUES (1, 'badge1', 1);
INSERT INTO branch (id, company_id, code) VALUES (1, 1, 'hq');
INSERT INTO account (id, iban, tenant) VALUES (1, 'iban1', 'acme');
