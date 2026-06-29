-- Integration-test seed for the Summit migration slice.
--
-- Portable slice DDL (Oracle TABLESPACE "SYSTEM" / storage clauses stripped per the
-- summit-dump-load-gotchas note) plus a small, deterministic dataset. This runs once
-- per container (gvenzl/oracle-free) as the test user, so the same unqualified SQL the
-- repository runs in production resolves against these tables here.
--
-- Out-of-slice FKs (S_EMP -> DEPT/TITLE/self, S_CUSTOMER -> REGION) are dropped so the
-- seed is FK-closed within the slice. Later slices extend this file (S_PRODUCT, S_ITEM).

CREATE TABLE s_emp (
  id         NUMBER(7,0)  NOT NULL,
  last_name  VARCHAR2(25) NOT NULL,
  first_name VARCHAR2(25),
  CONSTRAINT s_emp_id_pk PRIMARY KEY (id)
);

CREATE TABLE s_customer (
  id            NUMBER(7,0)  NOT NULL,
  name          VARCHAR2(50) NOT NULL,
  credit_rating VARCHAR2(9),
  sales_rep_id  NUMBER(7,0),
  CONSTRAINT s_customer_id_pk PRIMARY KEY (id),
  CONSTRAINT s_customer_credit_rating_ck CHECK (credit_rating IN ('EXCELLENT','GOOD','POOR')),
  CONSTRAINT s_customer_sales_rep_id_fk FOREIGN KEY (sales_rep_id) REFERENCES s_emp(id)
);

CREATE TABLE s_ord (
  id           NUMBER(7,0)  NOT NULL,
  customer_id  NUMBER(7,0)  NOT NULL,
  date_ordered DATE,
  date_shipped DATE,
  sales_rep_id NUMBER(7,0),
  total        NUMBER(11,2),
  payment_type VARCHAR2(6),
  order_filled VARCHAR2(1),
  CONSTRAINT s_ord_id_pk PRIMARY KEY (id),
  CONSTRAINT s_ord_payment_type_ck CHECK (payment_type IN ('CASH','CREDIT')),
  CONSTRAINT s_ord_order_filled_ck CHECK (order_filled IN ('Y','N')),
  CONSTRAINT s_ord_customer_id_fk  FOREIGN KEY (customer_id)  REFERENCES s_customer(id),
  CONSTRAINT s_ord_sales_rep_id_fk FOREIGN KEY (sales_rep_id) REFERENCES s_emp(id)
);

INSERT INTO s_emp (id, last_name, first_name) VALUES (1, 'Magee', 'Colin');
INSERT INTO s_emp (id, last_name, first_name) VALUES (2, 'Giljum', 'Henry');

INSERT INTO s_customer (id, name, credit_rating, sales_rep_id) VALUES (201, 'Unisports', 'EXCELLENT', 1);
INSERT INTO s_customer (id, name, credit_rating, sales_rep_id) VALUES (202, 'Womansport', 'GOOD', 2);

INSERT INTO s_ord (id, customer_id, date_ordered, date_shipped, sales_rep_id, total, payment_type, order_filled)
  VALUES (50001, 201, DATE '1992-08-31', DATE '1992-09-10', 1, 601.00, 'CASH', 'Y');
INSERT INTO s_ord (id, customer_id, date_ordered, date_shipped, sales_rep_id, total, payment_type, order_filled)
  VALUES (50002, 202, DATE '1992-09-01', NULL, 2, 8056.60, 'CREDIT', 'N');
INSERT INTO s_ord (id, customer_id, date_ordered, date_shipped, sales_rep_id, total, payment_type, order_filled)
  VALUES (50003, 201, DATE '1992-09-02', NULL, NULL, 0.00, 'CASH', 'N')
