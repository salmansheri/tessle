IF DB_ID('tenant') IS NULL CREATE DATABASE tenant;
GO
USE tenant;
GO
-- Sample schema shared by every tenant test database (same tables and rows in all four dialects).
CREATE TABLE customers (
  id         INTEGER PRIMARY KEY,
  name       VARCHAR(100) NOT NULL,
  email      VARCHAR(150) NOT NULL,
  country    VARCHAR(2)   NOT NULL,
  created_at DATETIME2    NOT NULL
);
GO
CREATE TABLE orders (
  id          INTEGER PRIMARY KEY,
  customer_id INTEGER       NOT NULL REFERENCES customers (id),
  status      VARCHAR(20)   NOT NULL,
  total       DECIMAL(10,2) NOT NULL,
  placed_at   DATETIME2     NOT NULL
);
GO
CREATE TABLE invoices (
  id        INTEGER PRIMARY KEY,
  order_id  INTEGER       NOT NULL REFERENCES orders (id),
  issued_on DATE          NOT NULL,
  paid      BIT       NOT NULL,
  amount    DECIMAL(10,2) NOT NULL
);
GO
INSERT INTO customers (id, name, email, country, created_at) VALUES
  (1, 'Ada Lovelace', 'ada@example.test', 'GB', '2025-01-10 09:00:00'),
  (2, 'Grace Hopper', 'grace@example.test', 'US', '2025-02-14 12:30:00'),
  (3, 'Linus Torvalds', 'linus@example.test', 'FI', '2025-03-01 08:15:00');
GO
INSERT INTO orders (id, customer_id, status, total, placed_at) VALUES
  (1, 1, 'shipped', 120.50, '2025-04-01 10:00:00'),
  (2, 1, 'pending',  35.00, '2025-04-05 11:20:00'),
  (3, 2, 'shipped', 980.00, '2025-04-07 14:45:00'),
  (4, 3, 'cancelled', 15.25, '2025-04-09 16:05:00');
GO
INSERT INTO invoices (id, order_id, issued_on, paid, amount) VALUES
  (1, 1, '2025-04-02', 1,  120.50),
  (2, 3, '2025-04-08', 0, 980.00),
  (3, 2, '2025-04-06', 0,  35.00);
GO
