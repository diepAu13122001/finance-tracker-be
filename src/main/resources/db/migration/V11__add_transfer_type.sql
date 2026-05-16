-- src/main/resources/db/migration/V11__add_transfer_type.sql
ALTER TABLE transactions
DROP CONSTRAINT IF EXISTS transactions_type_check;

ALTER TABLE transactions ADD CONSTRAINT transactions_type_check CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER'));