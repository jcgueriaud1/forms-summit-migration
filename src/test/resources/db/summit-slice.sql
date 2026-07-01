-- Integration-test seed for the Summit migration slice.
--
-- Portable slice DDL (Oracle TABLESPACE "SYSTEM" / storage clauses stripped per the
-- summit-dump-load-gotchas note) plus a small, deterministic dataset. This runs once
-- per container (gvenzl/oracle-free) as the test user, so the same unqualified SQL the
-- repository runs in production resolves against these tables here.
--
-- Out-of-slice FKs (S_EMP -> DEPT/TITLE/self, S_CUSTOMER -> REGION) are dropped so the
-- seed is FK-closed within the slice. S_PRODUCT / S_ITEM extend the spine for the
-- read-only line-items grid (issue #11); S_PRODUCT out-of-slice FKs (LONGTEXT, IMAGE)
-- are likewise dropped.

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
-- A POOR-rated customer (no orders of its own) — the foil for the WHEN-RADIO-CHANGED credit guard
-- (issue #18): typing this id and selecting CREDIT is refused and reset to CASH. 'POOR' exists in
-- the full Summit dump; this slice carries one such customer so the guard can be proven end-to-end.
INSERT INTO s_customer (id, name, credit_rating, sales_rep_id) VALUES (290, 'Kam''s Sporting Goods', 'POOR', 1);

INSERT INTO s_ord (id, customer_id, date_ordered, date_shipped, sales_rep_id, total, payment_type, order_filled)
  VALUES (50001, 201, DATE '1992-08-31', DATE '1992-09-10', 1, 601.00, 'CASH', 'Y');
INSERT INTO s_ord (id, customer_id, date_ordered, date_shipped, sales_rep_id, total, payment_type, order_filled)
  VALUES (50002, 202, DATE '1992-09-01', NULL, 2, 8056.60, 'CREDIT', 'N');
INSERT INTO s_ord (id, customer_id, date_ordered, date_shipped, sales_rep_id, total, payment_type, order_filled)
  VALUES (50003, 201, DATE '1992-09-02', NULL, NULL, 0.00, 'CASH', 'N');

CREATE TABLE s_product (
  id                    NUMBER(7,0)  NOT NULL,
  name                  VARCHAR2(50) NOT NULL,
  short_desc            VARCHAR2(255),
  suggested_whlsl_price NUMBER(11,2),
  CONSTRAINT s_product_id_pk PRIMARY KEY (id),
  CONSTRAINT s_product_name_uk UNIQUE (name)
);

CREATE TABLE s_item (
  ord_id           NUMBER(7,0) NOT NULL,
  item_id          NUMBER(7,0) NOT NULL,
  product_id       NUMBER(7,0) NOT NULL,
  price            NUMBER(11,2),
  quantity         NUMBER(9,0),
  quantity_shipped NUMBER(9,0),
  CONSTRAINT s_item_pk            PRIMARY KEY (ord_id, item_id),
  CONSTRAINT s_item_ord_product_uk UNIQUE (ord_id, product_id),
  CONSTRAINT s_item_ord_id_fk     FOREIGN KEY (ord_id)     REFERENCES s_ord(id),
  CONSTRAINT s_item_product_id_fk FOREIGN KEY (product_id) REFERENCES s_product(id)
);

INSERT INTO s_product (id, name, short_desc, suggested_whlsl_price) VALUES (10011, 'Bunny Boot', 'Beginner ski boot', 56.60);
INSERT INTO s_product (id, name, short_desc, suggested_whlsl_price) VALUES (20108, 'Ski Pole', 'Aluminium ski pole', 13.00);
INSERT INTO s_product (id, name, short_desc, suggested_whlsl_price) VALUES (30421, 'Pro Ski Boot', 'Competition ski boot', 1000.00);
INSERT INTO s_product (id, name, short_desc, suggested_whlsl_price) VALUES (41010, 'Ski Boots', 'All-mountain ski boot', 50.00);

-- Line items. item_total = quantity_shipped * price (per orders-form-spec.md), so each
-- order's lines sum to its S_ORD.total: 50001 -> 601.00, 50002 -> 8056.60, 50003 -> none.
INSERT INTO s_item (ord_id, item_id, product_id, price, quantity, quantity_shipped) VALUES (50001, 1, 41010,   50.00,  5,  5);
INSERT INTO s_item (ord_id, item_id, product_id, price, quantity, quantity_shipped) VALUES (50001, 2, 20108,   13.00, 27, 27);
INSERT INTO s_item (ord_id, item_id, product_id, price, quantity, quantity_shipped) VALUES (50002, 1, 30421, 1000.00,  8,  8);
INSERT INTO s_item (ord_id, item_id, product_id, price, quantity, quantity_shipped) VALUES (50002, 2, 10011,   56.60,  1,  1);

-- Whole-graph commit slice (issue #19). The S_ORD PRE-INSERT draws new order ids from this
-- sequence; it starts well above the seeded orders (50001-50003) and the delete IT's throwaway
-- (59999) so a nextval-assigned insert never collides with fixed test data.
CREATE SEQUENCE s_ord_id START WITH 70000 INCREMENT BY 1 NOCACHE;

-- The AUDIT_S_ITEM trigger target (S_ITEM_AUDIT.A NUMBER — captures no actor, per orders-form-spec).
-- The trigger itself is a PL/SQL block, so it is created by the container harness rather than here
-- (the init-script splitter cannot parse the inner statement terminators); see SummitOracleContainer.
CREATE TABLE s_item_audit (
  a NUMBER
)
