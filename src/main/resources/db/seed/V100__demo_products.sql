-- Demo data, only loaded with the "local" profile (see application-local.yml).
INSERT INTO product (sku, name, description, category, price, high_demand, created_at) VALUES
  ('CONSOLE-X',   'Games Console X (launch edition)', 'Limited launch stock',       'gaming',      499.99, TRUE,  now() - interval '2 hours'),
  ('SNEAKER-LTD', 'Limited Edition Trainers',         'Collaboration drop',         'fashion',     180.00, TRUE,  now() - interval '1 day'),
  ('KETTLE-01',   'Electric Kettle 1.7L',             'Rapid boil',                 'home',         34.99, FALSE, now() - interval '3 days'),
  ('MUG-LDN',     'London Skyline Mug',               'Dishwasher safe',            'home',          9.50, FALSE, now() - interval '10 days'),
  ('BOOK-JAVA',   'Modern Java in Practice',          'Paperback',                  'books',        39.00, FALSE, now() - interval '5 minutes'),
  ('HEADPH-NC',   'Noise Cancelling Headphones',      'Over-ear, 30h battery',      'electronics', 249.00, FALSE, now() - interval '20 days');

INSERT INTO inventory (product_id, on_hand)
SELECT id, CASE WHEN high_demand THEN 25 ELSE 500 END FROM product;
