-- Sample schema shared by every tenant test database (same tables and rows in all four dialects).
CREATE TABLE customers (
  id         NUMBER(10) PRIMARY KEY,
  name       VARCHAR2(100) NOT NULL,
  email      VARCHAR2(150) NOT NULL,
  country    VARCHAR2(2)   NOT NULL,
  created_at TIMESTAMP    NOT NULL
);
CREATE TABLE orders (
  id          NUMBER(10) PRIMARY KEY,
  customer_id NUMBER(10)       NOT NULL REFERENCES customers (id),
  status      VARCHAR2(20)   NOT NULL,
  total       DECIMAL(10,2) NOT NULL,
  placed_at   TIMESTAMP     NOT NULL
);
CREATE TABLE invoices (
  id        NUMBER(10) PRIMARY KEY,
  order_id  NUMBER(10)       NOT NULL REFERENCES orders (id),
  issued_on DATE          NOT NULL,
  paid      BOOLEAN       NOT NULL,
  amount    DECIMAL(10,2) NOT NULL
);
INSERT INTO customers (id, name, email, country, created_at) VALUES
  (1, 'Ada Lovelace', 'ada@example.test', 'GB', TIMESTAMP '2025-01-10 09:00:00'),
  (2, 'Grace Hopper', 'grace@example.test', 'US', TIMESTAMP '2025-02-14 12:30:00'),
  (3, 'Linus Torvalds', 'linus@example.test', 'FI', TIMESTAMP '2025-03-01 08:15:00');
INSERT INTO orders (id, customer_id, status, total, placed_at) VALUES
  (1, 1, 'shipped', 120.50, TIMESTAMP '2025-04-01 10:00:00'),
  (2, 1, 'pending',  35.00, TIMESTAMP '2025-04-05 11:20:00'),
  (3, 2, 'shipped', 980.00, TIMESTAMP '2025-04-07 14:45:00'),
  (4, 3, 'cancelled', 15.25, TIMESTAMP '2025-04-09 16:05:00');
INSERT INTO invoices (id, order_id, issued_on, paid, amount) VALUES
  (1, 1, DATE '2025-04-02', TRUE,  120.50),
  (2, 3, DATE '2025-04-08', FALSE, 980.00),
  (3, 2, DATE '2025-04-06', FALSE,  35.00);
