CREATE TABLE bills (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    billing_date DATE NOT NULL,
    category VARCHAR(64) NOT NULL,
    paid BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_bills_amount CHECK (amount >= 0.01)
);

CREATE INDEX idx_bills_billing_date_id ON bills (billing_date, id);
