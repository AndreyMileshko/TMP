-- COL-011 / TMP-SPEC-015 section 16.4 / ADR-039
-- Initial Reference Data Colors seed from approved Excel (860 colors / 245 SO7 mappings).
-- Keys are precomputed with Java Locale.ROOT / section 6.2 rules; PostgreSQL upper()/lower() are not used.
-- Excel illustrative IDs are not used as database identifiers.

-- 860 TMP colors
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'Белый', 'БЕЛЫЙ', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), '00', '00', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1000', 'RAL1000', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1000|30', 'RAL1000|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1000|МУАР', 'RAL1000|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1001', 'RAL1001', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1001|30', 'RAL1001|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1001|МУАР', 'RAL1001|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1002', 'RAL1002', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1002|30', 'RAL1002|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1002|МУАР', 'RAL1002|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1003', 'RAL1003', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1003|30', 'RAL1003|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1003|МУАР', 'RAL1003|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1004', 'RAL1004', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1004|30', 'RAL1004|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1004|МУАР', 'RAL1004|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1005', 'RAL1005', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1005|30', 'RAL1005|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1005|МУАР', 'RAL1005|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1006', 'RAL1006', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1006|30', 'RAL1006|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1006|МУАР', 'RAL1006|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1007', 'RAL1007', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1007|30', 'RAL1007|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1007|МУАР', 'RAL1007|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1011', 'RAL1011', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1011|30', 'RAL1011|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1011|МУАР', 'RAL1011|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1012', 'RAL1012', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1012|30', 'RAL1012|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1012|МУАР', 'RAL1012|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1013', 'RAL1013', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1013|30', 'RAL1013|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1013|МУАР', 'RAL1013|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1014', 'RAL1014', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1014|30', 'RAL1014|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1014|МУАР', 'RAL1014|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1015', 'RAL1015', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1015|30', 'RAL1015|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1015|МУАР', 'RAL1015|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1016', 'RAL1016', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1016|30', 'RAL1016|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1016|МУАР', 'RAL1016|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1017', 'RAL1017', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1017|30', 'RAL1017|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1017|МУАР', 'RAL1017|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1018', 'RAL1018', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1018|30', 'RAL1018|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1018|МУАР', 'RAL1018|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1019', 'RAL1019', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1019|30', 'RAL1019|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1019|МУАР', 'RAL1019|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1020', 'RAL1020', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1020|30', 'RAL1020|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1020|МУАР', 'RAL1020|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1021', 'RAL1021', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1021|30', 'RAL1021|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1021|МУАР', 'RAL1021|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1023', 'RAL1023', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1023|30', 'RAL1023|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1023|МУАР', 'RAL1023|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1024', 'RAL1024', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1024|30', 'RAL1024|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1024|МУАР', 'RAL1024|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1026', 'RAL1026', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1026|30', 'RAL1026|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1026|МУАР', 'RAL1026|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1027', 'RAL1027', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1027|30', 'RAL1027|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1027|МУАР', 'RAL1027|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1028', 'RAL1028', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1028|30', 'RAL1028|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1028|МУАР', 'RAL1028|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1032', 'RAL1032', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1032|30', 'RAL1032|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1032|МУАР', 'RAL1032|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1033', 'RAL1033', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1033|30', 'RAL1033|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1033|МУАР', 'RAL1033|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1034', 'RAL1034', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1034|30', 'RAL1034|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1034|МУАР', 'RAL1034|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1035', 'RAL1035', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1035|30', 'RAL1035|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1035|МУАР', 'RAL1035|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1036', 'RAL1036', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1036|30', 'RAL1036|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1036|МУАР', 'RAL1036|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1037', 'RAL1037', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1037|30', 'RAL1037|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL1037|МУАР', 'RAL1037|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2000', 'RAL2000', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2000|30', 'RAL2000|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2000|МУАР', 'RAL2000|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2001', 'RAL2001', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2001|30', 'RAL2001|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2001|МУАР', 'RAL2001|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2002', 'RAL2002', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2002|30', 'RAL2002|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2002|МУАР', 'RAL2002|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2003', 'RAL2003', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2003|30', 'RAL2003|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2003|МУАР', 'RAL2003|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2004', 'RAL2004', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2004|30', 'RAL2004|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2004|МУАР', 'RAL2004|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2005', 'RAL2005', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2005|30', 'RAL2005|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2005|МУАР', 'RAL2005|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2007', 'RAL2007', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2007|30', 'RAL2007|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2007|МУАР', 'RAL2007|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2008', 'RAL2008', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2008|30', 'RAL2008|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2008|МУАР', 'RAL2008|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2009', 'RAL2009', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2009|30', 'RAL2009|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2009|МУАР', 'RAL2009|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2010', 'RAL2010', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2010|30', 'RAL2010|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2010|МУАР', 'RAL2010|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2011', 'RAL2011', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2011|30', 'RAL2011|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2011|МУАР', 'RAL2011|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2012', 'RAL2012', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2012|30', 'RAL2012|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2012|МУАР', 'RAL2012|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2013', 'RAL2013', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2013|30', 'RAL2013|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL2013|МУАР', 'RAL2013|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3000', 'RAL3000', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3000|30', 'RAL3000|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3000|МУАР', 'RAL3000|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3001', 'RAL3001', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3001|30', 'RAL3001|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3001|МУАР', 'RAL3001|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3002', 'RAL3002', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3002|30', 'RAL3002|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3002|МУАР', 'RAL3002|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3003', 'RAL3003', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3003|30', 'RAL3003|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3003|МУАР', 'RAL3003|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3004', 'RAL3004', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3004|30', 'RAL3004|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3004|МУАР', 'RAL3004|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3005', 'RAL3005', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3005|30', 'RAL3005|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3005|МУАР', 'RAL3005|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3007', 'RAL3007', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3007|30', 'RAL3007|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3007|МУАР', 'RAL3007|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3009', 'RAL3009', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3009|30', 'RAL3009|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3009|МУАР', 'RAL3009|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3011', 'RAL3011', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3011|30', 'RAL3011|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3011|МУАР', 'RAL3011|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3012', 'RAL3012', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3012|30', 'RAL3012|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3012|МУАР', 'RAL3012|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3013', 'RAL3013', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3013|30', 'RAL3013|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3013|МУАР', 'RAL3013|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3014', 'RAL3014', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3014|30', 'RAL3014|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3014|МУАР', 'RAL3014|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3015', 'RAL3015', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3015|30', 'RAL3015|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3015|МУАР', 'RAL3015|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3016', 'RAL3016', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3016|30', 'RAL3016|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3016|МУАР', 'RAL3016|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3017', 'RAL3017', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3017|30', 'RAL3017|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3017|МУАР', 'RAL3017|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3018', 'RAL3018', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3018|30', 'RAL3018|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3018|МУАР', 'RAL3018|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3020', 'RAL3020', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3020|30', 'RAL3020|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3020|МУАР', 'RAL3020|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3022', 'RAL3022', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3022|30', 'RAL3022|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3022|МУАР', 'RAL3022|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3024', 'RAL3024', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3024|30', 'RAL3024|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3024|МУАР', 'RAL3024|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3026', 'RAL3026', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3026|30', 'RAL3026|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3026|МУАР', 'RAL3026|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3027', 'RAL3027', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3027|30', 'RAL3027|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3027|МУАР', 'RAL3027|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3028', 'RAL3028', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3028|30', 'RAL3028|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3028|МУАР', 'RAL3028|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3031', 'RAL3031', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3031|30', 'RAL3031|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3031|МУАР', 'RAL3031|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3032', 'RAL3032', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3032|30', 'RAL3032|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3032|МУАР', 'RAL3032|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3033', 'RAL3033', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3033|30', 'RAL3033|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL3033|МУАР', 'RAL3033|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4001', 'RAL4001', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4001|30', 'RAL4001|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4001|МУАР', 'RAL4001|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4002', 'RAL4002', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4002|30', 'RAL4002|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4002|МУАР', 'RAL4002|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4003', 'RAL4003', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4003|30', 'RAL4003|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4003|МУАР', 'RAL4003|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4004', 'RAL4004', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4004|30', 'RAL4004|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4004|МУАР', 'RAL4004|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4005', 'RAL4005', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4005|30', 'RAL4005|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4005|МУАР', 'RAL4005|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4006', 'RAL4006', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4006|30', 'RAL4006|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4006|МУАР', 'RAL4006|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4007', 'RAL4007', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4007|30', 'RAL4007|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4007|МУАР', 'RAL4007|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4008', 'RAL4008', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4008|30', 'RAL4008|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4008|МУАР', 'RAL4008|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4009', 'RAL4009', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4009|30', 'RAL4009|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4009|МУАР', 'RAL4009|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4010', 'RAL4010', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4010|30', 'RAL4010|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4010|МУАР', 'RAL4010|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4011', 'RAL4011', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4011|30', 'RAL4011|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4011|МУАР', 'RAL4011|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4012', 'RAL4012', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4012|30', 'RAL4012|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL4012|МУАР', 'RAL4012|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5000', 'RAL5000', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5000|30', 'RAL5000|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5000|МУАР', 'RAL5000|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5001', 'RAL5001', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5001|30', 'RAL5001|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5001|МУАР', 'RAL5001|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5002', 'RAL5002', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5002|30', 'RAL5002|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5002|МУАР', 'RAL5002|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5003', 'RAL5003', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5003|30', 'RAL5003|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5003|МУАР', 'RAL5003|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5004', 'RAL5004', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5004|30', 'RAL5004|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5004|МУАР', 'RAL5004|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5005', 'RAL5005', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5005|30', 'RAL5005|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5005|МУАР', 'RAL5005|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5007', 'RAL5007', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5007|30', 'RAL5007|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5007|МУАР', 'RAL5007|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5008', 'RAL5008', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5008|30', 'RAL5008|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5008|МУАР', 'RAL5008|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5009', 'RAL5009', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5009|30', 'RAL5009|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5009|МУАР', 'RAL5009|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5010', 'RAL5010', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5010|30', 'RAL5010|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5010|МУАР', 'RAL5010|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5011', 'RAL5011', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5011|30', 'RAL5011|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5011|МУАР', 'RAL5011|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5012', 'RAL5012', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5012|30', 'RAL5012|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5012|МУАР', 'RAL5012|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5013', 'RAL5013', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5013|30', 'RAL5013|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5013|МУАР', 'RAL5013|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5014', 'RAL5014', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5014|30', 'RAL5014|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5014|МУАР', 'RAL5014|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5015', 'RAL5015', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5015|30', 'RAL5015|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5015|МУАР', 'RAL5015|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5017', 'RAL5017', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5017|30', 'RAL5017|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5017|МУАР', 'RAL5017|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5018', 'RAL5018', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5018|30', 'RAL5018|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5018|МУАР', 'RAL5018|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5019', 'RAL5019', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5019|30', 'RAL5019|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5019|МУАР', 'RAL5019|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5020', 'RAL5020', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5020|30', 'RAL5020|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5020|МУАР', 'RAL5020|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5021', 'RAL5021', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5021|30', 'RAL5021|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5021|МУАР', 'RAL5021|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5022', 'RAL5022', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5022|30', 'RAL5022|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5022|МУАР', 'RAL5022|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5023', 'RAL5023', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5023|30', 'RAL5023|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5023|МУАР', 'RAL5023|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5024', 'RAL5024', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5024|30', 'RAL5024|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5024|МУАР', 'RAL5024|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5025', 'RAL5025', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5025|30', 'RAL5025|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5025|МУАР', 'RAL5025|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5026', 'RAL5026', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5026|30', 'RAL5026|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL5026|МУАР', 'RAL5026|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6000', 'RAL6000', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6000|30', 'RAL6000|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6000|МУАР', 'RAL6000|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6001', 'RAL6001', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6001|30', 'RAL6001|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6001|МУАР', 'RAL6001|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6002', 'RAL6002', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6002|30', 'RAL6002|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6002|МУАР', 'RAL6002|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6003', 'RAL6003', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6003|30', 'RAL6003|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6003|МУАР', 'RAL6003|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6004', 'RAL6004', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6004|30', 'RAL6004|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6004|МУАР', 'RAL6004|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6005', 'RAL6005', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6005|30', 'RAL6005|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6005|МУАР', 'RAL6005|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6006', 'RAL6006', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6006|30', 'RAL6006|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6006|МУАР', 'RAL6006|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6007', 'RAL6007', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6007|30', 'RAL6007|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6007|МУАР', 'RAL6007|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6008', 'RAL6008', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6008|30', 'RAL6008|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6008|МУАР', 'RAL6008|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6009', 'RAL6009', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6009|30', 'RAL6009|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6009|МУАР', 'RAL6009|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6010', 'RAL6010', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6010|30', 'RAL6010|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6010|МУАР', 'RAL6010|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6011', 'RAL6011', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6011|30', 'RAL6011|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6011|МУАР', 'RAL6011|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6012', 'RAL6012', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6012|30', 'RAL6012|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6012|МУАР', 'RAL6012|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6013', 'RAL6013', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6013|30', 'RAL6013|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6013|МУАР', 'RAL6013|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6014', 'RAL6014', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6014|30', 'RAL6014|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6014|МУАР', 'RAL6014|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6015', 'RAL6015', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6015|30', 'RAL6015|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6015|МУАР', 'RAL6015|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6016', 'RAL6016', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6016|30', 'RAL6016|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6016|МУАР', 'RAL6016|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6017', 'RAL6017', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6017|30', 'RAL6017|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6017|МУАР', 'RAL6017|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6018', 'RAL6018', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6018|30', 'RAL6018|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6018|МУАР', 'RAL6018|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6019', 'RAL6019', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6019|30', 'RAL6019|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6019|МУАР', 'RAL6019|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6020', 'RAL6020', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6020|30', 'RAL6020|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6020|МУАР', 'RAL6020|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6021', 'RAL6021', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6021|30', 'RAL6021|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6021|МУАР', 'RAL6021|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6022', 'RAL6022', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6022|30', 'RAL6022|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6022|МУАР', 'RAL6022|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6024', 'RAL6024', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6024|30', 'RAL6024|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6024|МУАР', 'RAL6024|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6025', 'RAL6025', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6025|30', 'RAL6025|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6025|МУАР', 'RAL6025|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6026', 'RAL6026', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6026|30', 'RAL6026|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6026|МУАР', 'RAL6026|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6027', 'RAL6027', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6027|30', 'RAL6027|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6027|МУАР', 'RAL6027|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6028', 'RAL6028', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6028|30', 'RAL6028|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6028|МУАР', 'RAL6028|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6029', 'RAL6029', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6029|30', 'RAL6029|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6029|МУАР', 'RAL6029|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6032', 'RAL6032', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6032|30', 'RAL6032|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6032|МУАР', 'RAL6032|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6033', 'RAL6033', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6033|30', 'RAL6033|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6033|МУАР', 'RAL6033|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6034', 'RAL6034', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6034|30', 'RAL6034|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6034|МУАР', 'RAL6034|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6035', 'RAL6035', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6035|30', 'RAL6035|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6035|МУАР', 'RAL6035|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6036', 'RAL6036', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6036|30', 'RAL6036|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6036|МУАР', 'RAL6036|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6037', 'RAL6037', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6037|30', 'RAL6037|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6037|МУАР', 'RAL6037|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6038', 'RAL6038', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6038|30', 'RAL6038|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL6038|МУАР', 'RAL6038|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7000', 'RAL7000', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7000|30', 'RAL7000|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7000|МУАР', 'RAL7000|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7001', 'RAL7001', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7001|30', 'RAL7001|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7001|МУАР', 'RAL7001|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7002', 'RAL7002', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7002|30', 'RAL7002|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7002|МУАР', 'RAL7002|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7003', 'RAL7003', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7003|30', 'RAL7003|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7003|МУАР', 'RAL7003|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7004', 'RAL7004', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7004|30', 'RAL7004|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7004|МУАР', 'RAL7004|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7005', 'RAL7005', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7005|30', 'RAL7005|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7005|МУАР', 'RAL7005|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7006', 'RAL7006', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7006|30', 'RAL7006|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7006|МУАР', 'RAL7006|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7008', 'RAL7008', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7008|30', 'RAL7008|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7008|МУАР', 'RAL7008|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7009', 'RAL7009', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7009|30', 'RAL7009|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7009|МУАР', 'RAL7009|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7010', 'RAL7010', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7010|30', 'RAL7010|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7010|МУАР', 'RAL7010|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7011', 'RAL7011', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7011|30', 'RAL7011|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7011|МУАР', 'RAL7011|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7012', 'RAL7012', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7012|30', 'RAL7012|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7012|МУАР', 'RAL7012|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7013', 'RAL7013', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7013|30', 'RAL7013|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7013|МУАР', 'RAL7013|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7015', 'RAL7015', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7015|30', 'RAL7015|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7015|МУАР', 'RAL7015|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7016', 'RAL7016', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7016|30', 'RAL7016|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7016|МУАР', 'RAL7016|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7021', 'RAL7021', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7021|30', 'RAL7021|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7021|МУАР', 'RAL7021|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7022', 'RAL7022', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7022|30', 'RAL7022|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7022|МУАР', 'RAL7022|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7023', 'RAL7023', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7023|30', 'RAL7023|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7023|МУАР', 'RAL7023|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7024', 'RAL7024', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7024|30', 'RAL7024|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7024|МУАР', 'RAL7024|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7026', 'RAL7026', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7026|30', 'RAL7026|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7026|МУАР', 'RAL7026|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7030', 'RAL7030', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7030|30', 'RAL7030|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7030|МУАР', 'RAL7030|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7031', 'RAL7031', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7031|30', 'RAL7031|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7031|МУАР', 'RAL7031|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7032', 'RAL7032', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7032|30', 'RAL7032|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7032|МУАР', 'RAL7032|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7033', 'RAL7033', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7033|30', 'RAL7033|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7033|МУАР', 'RAL7033|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7034', 'RAL7034', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7034|30', 'RAL7034|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7034|МУАР', 'RAL7034|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7035', 'RAL7035', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7035|30', 'RAL7035|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7035|МУАР', 'RAL7035|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7036', 'RAL7036', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7036|30', 'RAL7036|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7036|МУАР', 'RAL7036|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7037', 'RAL7037', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7037|30', 'RAL7037|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7037|МУАР', 'RAL7037|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7038', 'RAL7038', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7038|30', 'RAL7038|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7038|МУАР', 'RAL7038|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7039', 'RAL7039', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7039|30', 'RAL7039|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7039|МУАР', 'RAL7039|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7040', 'RAL7040', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7040|30', 'RAL7040|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7040|МУАР', 'RAL7040|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7042', 'RAL7042', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7042|30', 'RAL7042|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7042|МУАР', 'RAL7042|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7043', 'RAL7043', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7043|30', 'RAL7043|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7043|МУАР', 'RAL7043|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7044', 'RAL7044', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7044|30', 'RAL7044|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7044|МУАР', 'RAL7044|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7045', 'RAL7045', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7045|30', 'RAL7045|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7045|МУАР', 'RAL7045|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7046', 'RAL7046', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7046|30', 'RAL7046|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7046|МУАР', 'RAL7046|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7047', 'RAL7047', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7047|30', 'RAL7047|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7047|МУАР', 'RAL7047|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7048', 'RAL7048', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7048|30', 'RAL7048|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL7048|МУАР', 'RAL7048|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8000', 'RAL8000', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8000|30', 'RAL8000|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8000|МУАР', 'RAL8000|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8001', 'RAL8001', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8001|30', 'RAL8001|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8001|МУАР', 'RAL8001|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8002', 'RAL8002', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8002|30', 'RAL8002|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8002|МУАР', 'RAL8002|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8003', 'RAL8003', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8003|30', 'RAL8003|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8003|МУАР', 'RAL8003|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8004', 'RAL8004', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8004|30', 'RAL8004|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8004|МУАР', 'RAL8004|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8007', 'RAL8007', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8007|30', 'RAL8007|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8007|МУАР', 'RAL8007|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8008', 'RAL8008', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8008|30', 'RAL8008|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8008|МУАР', 'RAL8008|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8011', 'RAL8011', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8011|30', 'RAL8011|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8011|МУАР', 'RAL8011|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8012', 'RAL8012', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8012|30', 'RAL8012|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8012|МУАР', 'RAL8012|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8014', 'RAL8014', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8014|30', 'RAL8014|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8014|МУАР', 'RAL8014|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8015', 'RAL8015', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8015|30', 'RAL8015|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8015|МУАР', 'RAL8015|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8016', 'RAL8016', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8016|30', 'RAL8016|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8016|МУАР', 'RAL8016|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8017', 'RAL8017', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8017|30', 'RAL8017|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8017|МУАР', 'RAL8017|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8019', 'RAL8019', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8019|30', 'RAL8019|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8019|МУАР', 'RAL8019|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8022', 'RAL8022', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8022|30', 'RAL8022|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8022|МУАР', 'RAL8022|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8023', 'RAL8023', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8023|30', 'RAL8023|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8023|МУАР', 'RAL8023|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8024', 'RAL8024', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8024|30', 'RAL8024|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8024|МУАР', 'RAL8024|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8025', 'RAL8025', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8025|30', 'RAL8025|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8025|МУАР', 'RAL8025|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8028', 'RAL8028', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8028|30', 'RAL8028|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8028|МУАР', 'RAL8028|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8029', 'RAL8029', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8029|30', 'RAL8029|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL8029|МУАР', 'RAL8029|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9001', 'RAL9001', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9001|30', 'RAL9001|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9001|МУАР', 'RAL9001|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9002', 'RAL9002', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9002|30', 'RAL9002|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9002|МУАР', 'RAL9002|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9003', 'RAL9003', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9003|30', 'RAL9003|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9003|МУАР', 'RAL9003|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9004', 'RAL9004', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9004|30', 'RAL9004|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9004|МУАР', 'RAL9004|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9005', 'RAL9005', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9005|30', 'RAL9005|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9005|МУАР', 'RAL9005|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9006', 'RAL9006', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9006|30', 'RAL9006|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9006|МУАР', 'RAL9006|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9007', 'RAL9007', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9007|30', 'RAL9007|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9007|МУАР', 'RAL9007|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9010', 'RAL9010', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9010|30', 'RAL9010|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9010|МУАР', 'RAL9010|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9011', 'RAL9011', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9011|30', 'RAL9011|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9011|МУАР', 'RAL9011|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9016', 'RAL9016', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9016|30', 'RAL9016|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9016|МУАР', 'RAL9016|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9017', 'RAL9017', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9017|30', 'RAL9017|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9017|МУАР', 'RAL9017|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9018', 'RAL9018', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9018|30', 'RAL9018|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9018|МУАР', 'RAL9018|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9022', 'RAL9022', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9022|30', 'RAL9022|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9022|МУАР', 'RAL9022|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9023', 'RAL9023', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9023|30', 'RAL9023|30', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'RAL9023|МУАР', 'RAL9023|МУАР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'B2302-G7', 'B2302-G7', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'B2302-G7|МАСС', 'B2302-G7|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'B2302-G7|ПРИТВОР', 'B2302-G7|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'B2303-G7', 'B2303-G7', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'B2303-G7|МАСС', 'B2303-G7|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'B2303-G7|ПРИТВОР', 'B2303-G7|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'B2304-G7', 'B2304-G7', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'B2304-G7|МАСС', 'B2304-G7|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'B2304-G7|ПРИТВОР', 'B2304-G7|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'BER77-Z8', 'BER77-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'BER77-Z8|МАСС', 'BER77-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'BER77-Z8|ПРИТВОР', 'BER77-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'BES89-Z8', 'BES89-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'BES89-Z8|МАСС', 'BES89-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'BES89-Z8|ПРИТВОР', 'BES89-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'D2502-F7', 'D2502-F7', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'D2502-F7|МАСС', 'D2502-F7|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'D2502-F7|ПРИТВОР', 'D2502-F7|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'D2503-F7', 'D2503-F7', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'D2503-F7|МАСС', 'D2503-F7|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'D2503-F7|ПРИТВОР', 'D2503-F7|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'D3202-Z6', 'D3202-Z6', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'D3202-Z6|МАСС', 'D3202-Z6|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'D3202-Z6|ПРИТВОР', 'D3202-Z6|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'DJ603-42', 'DJ603-42', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'DJ603-42|МАСС', 'DJ603-42|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'DJ603-42|ПРИТВОР', 'DJ603-42|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'DJ606-42', 'DJ606-42', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'DJ606-42|МАСС', 'DJ606-42|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'DJ606-42|ПРИТВОР', 'DJ606-42|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'FZP20-Z3', 'FZP20-Z3', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'FZP20-Z3|МАСС', 'FZP20-Z3|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'FZP20-Z3|ПРИТВОР', 'FZP20-Z3|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G0302-G7', 'G0302-G7', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G0302-G7|МАСС', 'G0302-G7|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G0302-G7|ПРИТВОР', 'G0302-G7|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G1501-Z8', 'G1501-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G1501-Z8|МАСС', 'G1501-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G1501-Z8|ПРИТВОР', 'G1501-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G3001-G7', 'G3001-G7', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G3001-G7|МАСС', 'G3001-G7|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G3001-G7|ПРИТВОР', 'G3001-G7|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G4301-G7', 'G4301-G7', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G4301-G7|МАСС', 'G4301-G7|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G4301-G7|ПРИТВОР', 'G4301-G7|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G8101-G7', 'G8101-G7', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G8101-G7|МАСС', 'G8101-G7|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G8101-G7|ПРИТВОР', 'G8101-G7|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G8201-F7', 'G8201-F7', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G8201-F7|МАСС', 'G8201-F7|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G8201-F7|ПРИТВОР', 'G8201-F7|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G8202-F7', 'G8202-F7', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G8202-F7|МАСС', 'G8202-F7|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'G8202-F7|ПРИТВОР', 'G8202-F7|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'GAP45-Z8', 'GAP45-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'GAP45-Z8|МАСС', 'GAP45-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'GAP45-Z8|ПРИТВОР', 'GAP45-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'GF401-5F', 'GF401-5F', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'GF401-5F|МАСС', 'GF401-5F|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'GF401-5F|ПРИТВОР', 'GF401-5F|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'GF402-5F', 'GF402-5F', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'GF402-5F|МАСС', 'GF402-5F|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'GF402-5F|ПРИТВОР', 'GF402-5F|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'GH401-9G', 'GH401-9G', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'GH401-9G|МАСС', 'GH401-9G|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'GH401-9G|ПРИТВОР', 'GH401-9G|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'GH402-9G', 'GH402-9G', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'GH402-9G|МАСС', 'GH402-9G|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'GH402-9G|ПРИТВОР', 'GH402-9G|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'GO502-Z8', 'GO502-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'GO502-Z8|МАСС', 'GO502-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'GO502-Z8|ПРИТВОР', 'GO502-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KACH7-Z8', 'KACH7-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KACH7-Z8|МАСС', 'KACH7-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KACH7-Z8|ПРИТВОР', 'KACH7-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KACV8-Z8', 'KACV8-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KACV8-Z8|МАСС', 'KACV8-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KACV8-Z8|ПРИТВОР', 'KACV8-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KADF8-Z8', 'KADF8-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KADF8-Z8|МАСС', 'KADF8-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KADF8-Z8|ПРИТВОР', 'KADF8-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KAEX9-82', 'KAEX9-82', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KAEX9-82|МАСС', 'KAEX9-82|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KAEX9-82|ПРИТВОР', 'KAEX9-82|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KAEX9-Z8', 'KAEX9-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KAEX9-Z8|МАСС', 'KAEX9-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KAEX9-Z8|ПРИТВОР', 'KAEX9-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KAGF3-F7', 'KAGF3-F7', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KAGF3-F7|МАСС', 'KAGF3-F7|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KAGF3-F7|ПРИТВОР', 'KAGF3-F7|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB74-34', 'KDB74-34', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB74-34|МАСС', 'KDB74-34|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB74-34|ПРИТВОР', 'KDB74-34|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB74-6H', 'KDB74-6H', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB74-6H|МАСС', 'KDB74-6H|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB74-6H|ПРИТВОР', 'KDB74-6H|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB74-F7', 'KDB74-F7', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB74-F7|МАСС', 'KDB74-F7|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB74-F7|ПРИТВОР', 'KDB74-F7|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB74-Z8', 'KDB74-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB74-Z8|МАСС', 'KDB74-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB74-Z8|ПРИТВОР', 'KDB74-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB75-34', 'KDB75-34', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB75-34|МАСС', 'KDB75-34|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB75-34|ПРИТВОР', 'KDB75-34|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB75-69', 'KDB75-69', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB75-69|МАСС', 'KDB75-69|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB75-69|ПРИТВОР', 'KDB75-69|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB75-Z8', 'KDB75-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB75-Z8|МАСС', 'KDB75-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDB75-Z8|ПРИТВОР', 'KDB75-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDD17-U4', 'KDD17-U4', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDD17-U4|МАСС', 'KDD17-U4|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDD17-U4|ПРИТВОР', 'KDD17-U4|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDG14-69', 'KDG14-69', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDG14-69|МАСС', 'KDG14-69|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDG14-69|ПРИТВОР', 'KDG14-69|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDG62-69', 'KDG62-69', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDG62-69|МАСС', 'KDG62-69|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDG62-69|ПРИТВОР', 'KDG62-69|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDG62-6H', 'KDG62-6H', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDG62-6H|МАСС', 'KDG62-6H|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDG62-6H|ПРИТВОР', 'KDG62-6H|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDG62-F7', 'KDG62-F7', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDG62-F7|МАСС', 'KDG62-F7|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDG62-F7|ПРИТВОР', 'KDG62-F7|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDG62-Z8', 'KDG62-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDG62-Z8|МАСС', 'KDG62-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'KDG62-Z8|ПРИТВОР', 'KDG62-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'NDT46-Z8', 'NDT46-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'NDT46-Z8|МАСС', 'NDT46-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'NDT46-Z8|ПРИТВОР', 'NDT46-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'NDY05-Z8', 'NDY05-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'NDY05-Z8|МАСС', 'NDY05-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'NDY05-Z8|ПРИТВОР', 'NDY05-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'SS101-89', 'SS101-89', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'SS101-89|МАСС', 'SS101-89|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'SS101-89|ПРИТВОР', 'SS101-89|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UA114-Z3', 'UA114-Z3', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UA114-Z3|МАСС', 'UA114-Z3|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UA114-Z3|ПРИТВОР', 'UA114-Z3|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UF711-Z3', 'UF711-Z3', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UF711-Z3|МАСС', 'UF711-Z3|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UF711-Z3|ПРИТВОР', 'UF711-Z3|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UJ301-Z8', 'UJ301-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UJ301-Z8|МАСС', 'UJ301-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UJ301-Z8|ПРИТВОР', 'UJ301-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UJ401-Z8', 'UJ401-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UJ401-Z8|МАСС', 'UJ401-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UJ401-Z8|ПРИТВОР', 'UJ401-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UK101-Z8', 'UK101-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UK101-Z8|МАСС', 'UK101-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UK101-Z8|ПРИТВОР', 'UK101-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UK103-Z8', 'UK103-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UK103-Z8|МАСС', 'UK103-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UK103-Z8|ПРИТВОР', 'UK103-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UK117-34', 'UK117-34', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UK117-34|МАСС', 'UK117-34|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UK117-34|ПРИТВОР', 'UK117-34|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UK117-Z8', 'UK117-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UK117-Z8|МАСС', 'UK117-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UK117-Z8|ПРИТВОР', 'UK117-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UQ901-Z8', 'UQ901-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UQ901-Z8|МАСС', 'UQ901-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UQ901-Z8|ПРИТВОР', 'UQ901-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UQ902-Z8', 'UQ902-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UQ902-Z8|МАСС', 'UQ902-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UQ902-Z8|ПРИТВОР', 'UQ902-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR001-Z8', 'UR001-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR001-Z8|МАСС', 'UR001-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR001-Z8|ПРИТВОР', 'UR001-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR002-Z6', 'UR002-Z6', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR002-Z6|МАСС', 'UR002-Z6|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR002-Z6|ПРИТВОР', 'UR002-Z6|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR102-Z3', 'UR102-Z3', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR102-Z3|МАСС', 'UR102-Z3|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR102-Z3|ПРИТВОР', 'UR102-Z3|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR103-Z3', 'UR103-Z3', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR103-Z3|МАСС', 'UR103-Z3|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR103-Z3|ПРИТВОР', 'UR103-Z3|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR401-Z8', 'UR401-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR401-Z8|МАСС', 'UR401-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR401-Z8|ПРИТВОР', 'UR401-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR601-Z8', 'UR601-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR601-Z8|МАСС', 'UR601-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR601-Z8|ПРИТВОР', 'UR601-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR907-Z3', 'UR907-Z3', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR907-Z3|МАСС', 'UR907-Z3|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR907-Z3|ПРИТВОР', 'UR907-Z3|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR916-Z8', 'UR916-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR916-Z8|МАСС', 'UR916-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'UR916-Z8|ПРИТВОР', 'UR916-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'US601-Z8', 'US601-Z8', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'US601-Z8|МАСС', 'US601-Z8|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'US601-Z8|ПРИТВОР', 'US601-Z8|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'US805-U4', 'US805-U4', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'US805-U4|МАСС', 'US805-U4|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'US805-U4|ПРИТВОР', 'US805-U4|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'US906-Z3', 'US906-Z3', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'US906-Z3|МАСС', 'US906-Z3|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'US906-Z3|ПРИТВОР', 'US906-Z3|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'WAQ50-34', 'WAQ50-34', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'WAQ50-34|МАСС', 'WAQ50-34|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'WAQ50-34|ПРИТВОР', 'WAQ50-34|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'WAQ50-Z6', 'WAQ50-Z6', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'WAQ50-Z6|МАСС', 'WAQ50-Z6|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'WAQ50-Z6|ПРИТВОР', 'WAQ50-Z6|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'YEL88-34', 'YEL88-34', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'YEL88-34|МАСС', 'YEL88-34|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'YEL88-34|ПРИТВОР', 'YEL88-34|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'YEL88-U4', 'YEL88-U4', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'YEL88-U4|МАСС', 'YEL88-U4|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'YEL88-U4|ПРИТВОР', 'YEL88-U4|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'YEQ31-4K', 'YEQ31-4K', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'YEQ31-4K|МАСС', 'YEQ31-4K|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'YEQ31-4K|ПРИТВОР', 'YEQ31-4K|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'Z1402-J6', 'Z1402-J6', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'Z1402-J6|МАСС', 'Z1402-J6|МАСС', TRUE) ON CONFLICT (code_key) DO NOTHING;
INSERT INTO reference.colors (id, code, code_key, active) VALUES (gen_random_uuid(), 'Z1402-J6|ПРИТВОР', 'Z1402-J6|ПРИТВОР', TRUE) ON CONFLICT (code_key) DO NOTHING;

-- 245 SO7 mappings (inside/outside resolved by approved catalog code_key)
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'Белый', 'БЕЛЫЙ',
       (SELECT id FROM reference.colors WHERE code_key = 'БЕЛЫЙ'),
       (SELECT id FROM reference.colors WHERE code_key = 'БЕЛЫЙ'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9006 металлик', 'RAL 9006 МЕТАЛЛИК',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9006'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9006'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8017 коричневый', 'RAL 8017 КОРИЧНЕВЫЙ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7004 серый', 'RAL 7004 СЕРЫЙ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7004'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7004'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 5005 синий', 'RAL 5005 СИНИЙ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5005'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5005'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 6026 зеленый', 'RAL 6026 ЗЕЛЕНЫЙ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL6026'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL6026'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 3005 красный', 'RAL 3005 КРАСНЫЙ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL3005'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL3005'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 10130', 'RAL 10130',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1013'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1013'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 1013', 'RAL 1013',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1013'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1013'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7040 серый', 'RAL 7040 СЕРЫЙ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7040'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7040'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8024 коричневый', 'RAL 8024 КОРИЧНЕВЫЙ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8024'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8024'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8003 коричневый', 'RAL 8003 КОРИЧНЕВЫЙ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8003'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8003'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 3001 красный', 'RAL 3001 КРАСНЫЙ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL3001'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL3001'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7001', 'RAL 7001',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7001'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7001'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8028 коричневый', 'RAL 8028 КОРИЧНЕВЫЙ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8028'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8028'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 6016 зеленый', 'RAL 6016 ЗЕЛЕНЫЙ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL6016'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL6016'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL4008', 'RAL4008',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL4008'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL4008'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 3003 (красный)', 'RAL 3003 (КРАСНЫЙ)',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL3003'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL3003'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 5007 (синий)', 'RAL 5007 (СИНИЙ)',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5007'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5007'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL5011 (темн-синий)', 'RAL5011 (ТЕМН-СИНИЙ)',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5011'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5011'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 6005 (зеленый)', 'RAL 6005 (ЗЕЛЕНЫЙ)',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL6005'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL6005'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'Кремовый', 'КРЕМОВЫЙ',
       (SELECT id FROM reference.colors WHERE code_key = 'YEL88-U4'),
       (SELECT id FROM reference.colors WHERE code_key = 'YEL88-U4'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'Светло-серый', 'СВЕТЛО-СЕРЫЙ',
       (SELECT id FROM reference.colors WHERE code_key = 'KACH7-Z8'),
       (SELECT id FROM reference.colors WHERE code_key = 'KACH7-Z8'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8017', 'RAL 8017',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL9005', 'RAL9005',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9006', 'RAL 9006',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9006'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9006'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9010', 'RAL 9010',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9010'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9010'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL9016', 'RAL9016',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9016'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9016'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7016 мат', 'RAL 7016 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7004 мат', 'RAL 7004 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7004|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7004|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7024 мат', 'RAL 7024 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7024|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7024|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'Орех', 'ОРЕХ',
       (SELECT id FROM reference.colors WHERE code_key = 'UK103-Z8'),
       (SELECT id FROM reference.colors WHERE code_key = 'UK103-Z8'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9005 мат', 'RAL 9005 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8002 гл', 'RAL 8002 ГЛ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8002'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8002'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL7004 мат', 'RAL7004 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7004|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7004|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9011/30т', 'RAL 9011/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9011|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9011|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9011 мат тенд', 'RAL 9011 МАТ ТЕНД',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9011|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9011|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 1001 мат', 'RAL 1001 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1001|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1001|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7004 гл', 'RAL 7004 ГЛ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7004'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7004'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL7004 гл', 'RAL7004 ГЛ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7004'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7004'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 1014 гл', 'RAL 1014 ГЛ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1014'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1014'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8017 гл', 'RAL 8017 ГЛ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9001 мат', 'RAL 9001 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9001|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9001|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL9001 мат', 'RAL9001 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9001|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9001|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL8017 гл', 'RAL8017 ГЛ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8002 мат', 'RAL 8002 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8002|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8002|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7042 мат', 'RAL 7042 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7042|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7042|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7021', 'RAL 7021',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7021'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7021'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL9017 гл', 'RAL9017 ГЛ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9017'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9017'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'Золотой дуб', 'ЗОЛОТОЙ ДУБ',
       (SELECT id FROM reference.colors WHERE code_key = 'UK101-Z8'),
       (SELECT id FROM reference.colors WHERE code_key = 'UK101-Z8'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'тKDG62-6H', 'ТKDG62-6H',
       (SELECT id FROM reference.colors WHERE code_key = 'KDG62-6H'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDG62-6H'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL9004/30т', 'RAL9004/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9004|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9004|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL7016/30т', 'RAL7016/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7022 мат', 'RAL 7022 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7022|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7022|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 1015 гл', 'RAL 1015 ГЛ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1015'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1015'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9005 гл', 'RAL 9005 ГЛ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8016 мат', 'RAL 8016 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8016|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8016|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7015', 'RAL 7015',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7015'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7015'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9006 гл', 'RAL 9006 ГЛ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9006'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9006'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL8019', 'RAL8019',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8019'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8019'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8012', 'RAL 8012',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8012'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8012'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 3003', 'RAL 3003',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL3003'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL3003'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7040 мат', 'RAL 7040 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7040|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7040|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8017 мат', 'RAL 8017 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 1019', 'RAL 1019',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1019'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1019'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8011', 'RAL 8011',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8011'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8011'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7040', 'RAL 7040',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7040'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7040'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL7022/30т', 'RAL7022/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7022|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7022|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL7024/30т', 'RAL7024/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7024|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7024|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'US805-U4', 'US805-U4',
       (SELECT id FROM reference.colors WHERE code_key = 'US805-U4'),
       (SELECT id FROM reference.colors WHERE code_key = 'US805-U4'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL8017 мат', 'RAL8017 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 5005', 'RAL 5005',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5005'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5005'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7035 мат', 'RAL 7035 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7035|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7035|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7001 мат', 'RAL 7001 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7001|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7001|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL8019/30т', 'RAL8019/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8019|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8019|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL7016/30 притв', 'RAL7016/30 ПРИТВ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL9017/30т', 'RAL9017/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9017|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9017|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8019 мат', 'RAL 8019 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8019|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8019|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 1011 мат', 'RAL 1011 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1011|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1011|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7039 мат', 'RAL 7039 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7039|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7039|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7047 мат', 'RAL 7047 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7047|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7047|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7022', 'RAL 7022',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7022'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7022'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL7024/30 притв', 'RAL7024/30 ПРИТВ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7024|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7024|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL7024mat', 'RAL7024MAT',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7024|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7024|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL7022/30 притв', 'RAL7022/30 ПРИТВ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7022|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7022|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8022 мат', 'RAL 8022 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8022|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8022|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9003 мат', 'RAL 9003 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9003|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9003|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'тKDB74_Z8', 'ТKDB74_Z8',
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-Z8'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-Z8'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7037 мат', 'RAL 7037 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7037|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7037|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 5019', 'RAL 5019',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5019'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5019'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7043 мат', 'RAL 7043 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7043|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7043|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7003/30т', 'RAL 7003/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7003|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7003|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7021Сахара', 'RAL 7021САХАРА',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7021'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7021'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL9005АлютехПВС', 'RAL9005АЛЮТЕХПВС',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL8003', 'RAL8003',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8003'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8003'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7044 мат', 'RAL 7044 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7044|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7044|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8025', 'RAL 8025',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8025'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8025'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7039 мат притв', 'RAL 7039 МАТ ПРИТВ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7039|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7039|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7021 мат', 'RAL 7021 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7021|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7021|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7016 мат/ притв', 'RAL 7016 МАТ/ ПРИТВ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KDB74_34 притвор', 'KDB74_34 ПРИТВОР',
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-34|ПРИТВОР'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-34|ПРИТВОР'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KDB74_34', 'KDB74_34',
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-34'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-34'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL9017/30 притв', 'RAL9017/30 ПРИТВ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9017|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9017|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL9017 мат', 'RAL9017 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9017|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9017|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KDB74_F7т', 'KDB74_F7Т',
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-F7'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-F7'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7047 Мат притвор', 'RAL 7047 МАТ ПРИТВОР',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7047|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7047|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9007 мат', 'RAL 9007 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9007|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9007|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL7022 мат Лег.Сев', 'RAL7022 МАТ ЛЕГ.СЕВ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7022|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7022|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 1013 мат', 'RAL 1013 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1013|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1013|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL7016', 'RAL7016',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL9016м ПШ', 'RAL9016М ПШ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9016|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9016|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL8019м ПШ', 'RAL8019М ПШ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8019|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8019|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL9005м ПШ', 'RAL9005М ПШ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'Белый (RAL 9003)', 'БЕЛЫЙ (RAL 9003)',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9003'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9003'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 1036 мат', 'RAL 1036 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1036|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1036|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 5014', 'RAL 5014',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5014'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5014'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 3009/30т', 'RAL 3009/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL3009|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL3009|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7024 мат/притвор', 'RAL 7024 МАТ/ПРИТВОР',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7024|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7024|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7013мат', 'RAL 7013МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7013|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7013|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7031', 'RAL 7031',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7031'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7031'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7037', 'RAL 7037',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7037'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7037'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 5017', 'RAL 5017',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5017'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5017'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL7047п-мат', 'RAL7047П-МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7047|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7047|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL9004', 'RAL9004',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9004'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9004'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL9004п-мат*', 'RAL9004П-МАТ*',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9004|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9004|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7016 мат односто', 'RAL 7016 МАТ ОДНОСТО',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL7047/30т', 'RAL7047/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7047|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7047|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7021 мат/притвор', 'RAL 7021 МАТ/ПРИТВОР',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7021|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7021|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL7013/30т', 'RAL7013/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7013|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7013|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7024', 'RAL 7024',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7024'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7024'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KDB74-F7', 'KDB74-F7',
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-F7'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-F7'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL9005/30т', 'RAL9005/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'B2304-G7', 'B2304-G7',
       (SELECT id FROM reference.colors WHERE code_key = 'B2304-G7'),
       (SELECT id FROM reference.colors WHERE code_key = 'B2304-G7'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7012', 'RAL 7012',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7012'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7012'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL7021/30т', 'RAL7021/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7021|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7021|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7046 мат', 'RAL 7046 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7046|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7046|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7045 мат', 'RAL 7045 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7045|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7045|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9011_30т', 'RAL 9011_30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9011|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9011|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KDB75-Z8 Масс', 'KDB75-Z8 МАСС',
       (SELECT id FROM reference.colors WHERE code_key = 'KDB75-Z8|МАСС'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDB75-Z8|МАСС'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8028', 'RAL 8028',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8028'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8028'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8028 мат', 'RAL 8028 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8028|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8028|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7016', 'RAL 7016',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7016 односто', 'RAL 7016 ОДНОСТО',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8017 мат односто', 'RAL 8017 МАТ ОДНОСТО',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8017 односто', 'RAL 8017 ОДНОСТО',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KDB74-F7 Масс', 'KDB74-F7 МАСС',
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-F7|МАСС'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-F7|МАСС'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7012 мат', 'RAL 7012 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7012|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7012|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9005 мат+притвор', 'RAL 9005 МАТ+ПРИТВОР',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KDB75-Z8', 'KDB75-Z8',
       (SELECT id FROM reference.colors WHERE code_key = 'KDB75-Z8'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDB75-Z8'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7036', 'RAL 7036',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7036'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7036'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'GAP45', 'GAP45',
       (SELECT id FROM reference.colors WHERE code_key = 'GAP45-Z8'),
       (SELECT id FROM reference.colors WHERE code_key = 'GAP45-Z8'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL9002', 'RAL9002',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9002'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9002'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8003 мат', 'RAL 8003 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8003|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8003|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KDB74-6Hт', 'KDB74-6HТ',
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-6H'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-6H'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL7024/30т притв', 'RAL7024/30Т ПРИТВ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7024|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7024|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 3003гл. притвор', 'RAL 3003ГЛ. ПРИТВОР',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL3003'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL3003'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 5015 мат', 'RAL 5015 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5015|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5015|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9006 притвор', 'RAL 9006 ПРИТВОР',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9006'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9006'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7021/30т', 'RAL 7021/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7021|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7021|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7016 муар', 'RAL 7016 МУАР',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016|МУАР'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016|МУАР'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7016 муар/притво', 'RAL 7016 МУАР/ПРИТВО',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016|МУАР'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7016|МУАР'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'GF402-5F', 'GF402-5F',
       (SELECT id FROM reference.colors WHERE code_key = 'GF402-5F'),
       (SELECT id FROM reference.colors WHERE code_key = 'GF402-5F'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'GF402-5F притвор', 'GF402-5F ПРИТВОР',
       (SELECT id FROM reference.colors WHERE code_key = 'GF402-5F|ПРИТВОР'),
       (SELECT id FROM reference.colors WHERE code_key = 'GF402-5F|ПРИТВОР'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 1036/30т', 'RAL 1036/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1036|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1036|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL9016/30т', 'RAL9016/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9016|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9016|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KDG62-6H притвор', 'KDG62-6H ПРИТВОР',
       (SELECT id FROM reference.colors WHERE code_key = 'KDG62-6H|ПРИТВОР'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDG62-6H|ПРИТВОР'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'UK103', 'UK103',
       (SELECT id FROM reference.colors WHERE code_key = 'UK103-Z8'),
       (SELECT id FROM reference.colors WHERE code_key = 'UK103-Z8'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7034 мат', 'RAL 7034 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7034|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7034|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7021М т', 'RAL 7021М Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7021|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7021|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7021 Муар', 'RAL 7021 МУАР',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7021|МУАР'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7021|МУАР'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'B2303-G7', 'B2303-G7',
       (SELECT id FROM reference.colors WHERE code_key = 'B2303-G7'),
       (SELECT id FROM reference.colors WHERE code_key = 'B2303-G7'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9011 мат', 'RAL 9011 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9011|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9011|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9016мат', 'RAL 9016МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9016|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9016|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 3028', 'RAL 3028',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL3028'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL3028'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 5014 мат', 'RAL 5014 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5014|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5014|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 3009мат', 'RAL 3009МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL3009|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL3009|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'G3001-G7', 'G3001-G7',
       (SELECT id FROM reference.colors WHERE code_key = 'G3001-G7'),
       (SELECT id FROM reference.colors WHERE code_key = 'G3001-G7'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'US906-Z3', 'US906-Z3',
       (SELECT id FROM reference.colors WHERE code_key = 'US906-Z3'),
       (SELECT id FROM reference.colors WHERE code_key = 'US906-Z3'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7047', 'RAL 7047',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7047'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7047'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7024 муар', 'RAL 7024 МУАР',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7024|МУАР'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7024|МУАР'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8019', 'RAL 8019',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8019'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8019'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 2001 мат', 'RAL 2001 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL2001|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL2001|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8017/притвор', 'RAL 8017/ПРИТВОР',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8017мат/притв', 'RAL 8017МАТ/ПРИТВ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7006 мат', 'RAL 7006 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7006|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7006|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8004 мат', 'RAL 8004 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8004|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8004|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 1036_30', 'RAL 1036_30',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1036|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1036|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL1035/30т', 'RAL1035/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1035|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1035|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 1002', 'RAL 1002',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1002'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1002'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'DJ603-42', 'DJ603-42',
       (SELECT id FROM reference.colors WHERE code_key = 'DJ603-42'),
       (SELECT id FROM reference.colors WHERE code_key = 'DJ603-42'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7023 мат', 'RAL 7023 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7023|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7023|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7002 (Дуб)', 'RAL 7002 (ДУБ)',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7002'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7002'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KACV8-Z8', 'KACV8-Z8',
       (SELECT id FROM reference.colors WHERE code_key = 'KACV8-Z8'),
       (SELECT id FROM reference.colors WHERE code_key = 'KACV8-Z8'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL9005mat(Вышка)', 'RAL9005MAT(ВЫШКА)',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL9005mat(ЛБД)', 'RAL9005MAT(ЛБД)',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL7022mat(ЛБД)', 'RAL7022MAT(ЛБД)',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7022|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7022|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'UA114-Z3', 'UA114-Z3',
       (SELECT id FROM reference.colors WHERE code_key = 'UA114-Z3'),
       (SELECT id FROM reference.colors WHERE code_key = 'UA114-Z3'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'UA114', 'UA114',
       (SELECT id FROM reference.colors WHERE code_key = 'UA114-Z3'),
       (SELECT id FROM reference.colors WHERE code_key = 'UA114-Z3'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 6028 мат', 'RAL 6028 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL6028|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL6028|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 1011 гл', 'RAL 1011 ГЛ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1011'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1011'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KDB75-69', 'KDB75-69',
       (SELECT id FROM reference.colors WHERE code_key = 'KDB75-69'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDB75-69'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9005 муар', 'RAL 9005 МУАР',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005|МУАР'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9005|МУАР'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL9002/30т', 'RAL9002/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9002|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9002|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'GAP45-Z8', 'GAP45-Z8',
       (SELECT id FROM reference.colors WHERE code_key = 'GAP45-Z8'),
       (SELECT id FROM reference.colors WHERE code_key = 'GAP45-Z8'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KDG62-69', 'KDG62-69',
       (SELECT id FROM reference.colors WHERE code_key = 'KDG62-69'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDG62-69'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8014 мат', 'RAL 8014 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8014|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8014|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KDG14-69', 'KDG14-69',
       (SELECT id FROM reference.colors WHERE code_key = 'KDG14-69'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDG14-69'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL7040/30т', 'RAL7040/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7040|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7040|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL8008 мат', 'RAL8008 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8008|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8008|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 6004 мат', 'RAL 6004 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL6004|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL6004|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 1035/30т', 'RAL 1035/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1035|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1035|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 1035 мат', 'RAL 1035 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1035|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1035|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KDG62-6H_V', 'KDG62-6H_V',
       (SELECT id FROM reference.colors WHERE code_key = 'KDG62-6H'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDG62-6H'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8019 муар', 'RAL 8019 МУАР',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8019|МУАР'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8019|МУАР'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'UQ901-Z8_V', 'UQ901-Z8_V',
       (SELECT id FROM reference.colors WHERE code_key = 'UQ901-Z8'),
       (SELECT id FROM reference.colors WHERE code_key = 'UQ901-Z8'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9016.', 'RAL 9016.',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9016'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9016'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7010 мат', 'RAL 7010 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7010|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7010|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8017 Татпроф', 'RAL 8017 ТАТПРОФ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8017 TS', 'RAL 8017 TS',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8017'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'GH401-9G', 'GH401-9G',
       (SELECT id FROM reference.colors WHERE code_key = 'GH401-9G'),
       (SELECT id FROM reference.colors WHERE code_key = 'GH401-9G'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 1019/30', 'RAL 1019/30',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1019|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1019|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 1019/30т', 'RAL 1019/30Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1019|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL1019|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9003', 'RAL 9003',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9003'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9003'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8029 vf', 'RAL 8029 VF',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8029'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8029'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8029 мат', 'RAL 8029 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8029|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8029|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'NDT46-Z8', 'NDT46-Z8',
       (SELECT id FROM reference.colors WHERE code_key = 'NDT46-Z8'),
       (SELECT id FROM reference.colors WHERE code_key = 'NDT46-Z8'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8001', 'RAL 8001',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8001'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8001'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'UK103-Z8_V', 'UK103-Z8_V',
       (SELECT id FROM reference.colors WHERE code_key = 'UK103-Z8'),
       (SELECT id FROM reference.colors WHERE code_key = 'UK103-Z8'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KDB74-Z8', 'KDB74-Z8',
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-Z8'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-Z8'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KDB74-Z8_V', 'KDB74-Z8_V',
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-Z8'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-Z8'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 5005 мат', 'RAL 5005 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5005|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL5005|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7011', 'RAL 7011',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7011'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7011'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KDB74-6H', 'KDB74-6H',
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-6H'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDB74-6H'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KACH7-Z8', 'KACH7-Z8',
       (SELECT id FROM reference.colors WHERE code_key = 'KACH7-Z8'),
       (SELECT id FROM reference.colors WHERE code_key = 'KACH7-Z8'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7015 Mat Искра', 'RAL 7015 MAT ИСКРА',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7015|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7015|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 7015 мат', 'RAL 7015 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7015|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL7015|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8025 мат', 'RAL 8025 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8025|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8025|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 2004 мат', 'RAL 2004 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL2004|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL2004|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'UR401-Z8', 'UR401-Z8',
       (SELECT id FROM reference.colors WHERE code_key = 'UR401-Z8'),
       (SELECT id FROM reference.colors WHERE code_key = 'UR401-Z8'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KDB75-34_Масс', 'KDB75-34_МАСС',
       (SELECT id FROM reference.colors WHERE code_key = 'KDB75-34|МАСС'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDB75-34|МАСС'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'KDB75-34', 'KDB75-34',
       (SELECT id FROM reference.colors WHERE code_key = 'KDB75-34'),
       (SELECT id FROM reference.colors WHERE code_key = 'KDB75-34'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 8007 мат', 'RAL 8007 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8007|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL8007|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9016 Т', 'RAL 9016 Т',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9016'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9016'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 9016 TS', 'RAL 9016 TS',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9016'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL9016'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;
INSERT INTO reference.so7_color_mappings (id, source_text, source_key, inside_color_id, outside_color_id, active)
SELECT gen_random_uuid(), 'RAL 6009 мат', 'RAL 6009 МАТ',
       (SELECT id FROM reference.colors WHERE code_key = 'RAL6009|30'),
       (SELECT id FROM reference.colors WHERE code_key = 'RAL6009|30'),
       TRUE
ON CONFLICT (source_key) DO NOTHING;

-- System-wide automatic matching setting: OFF
INSERT INTO reference.color_matching_settings (id, automatic_matching_enabled) VALUES (1, FALSE) ON CONFLICT (id) DO NOTHING;
