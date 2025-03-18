-- select * from users.user;

-- SELECT schema_name
-- FROM information_schema.schemata;

-- SELECT table_name
-- FROM information_schema.tables
-- WHERE table_schema = 'users'

-- SELECT *
-- FROM information_schema.columns
-- WHERE table_name = 'user'

-- SELECT *
-- FROM information_schema.table_constraints
-- WHERE table_name = 'user'

SELECT *
FROM information_schema.key_column_usage
WHERE constraint_name = 'user_pkey'
AND table_name = 'user';