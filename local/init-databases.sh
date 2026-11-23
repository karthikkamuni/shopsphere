#!/bin/bash
# One database per microservice (database-per-service pattern).
# MYSQL_ROOT_PASSWORD and MYSQL_USER are provided by the MySQL container environment.
set -euo pipefail

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS userdb    CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS productdb CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS orderdb   CHARACTER SET utf8mb4;
GRANT ALL PRIVILEGES ON userdb.*    TO '${MYSQL_USER}'@'%';
GRANT ALL PRIVILEGES ON productdb.* TO '${MYSQL_USER}'@'%';
GRANT ALL PRIVILEGES ON orderdb.*   TO '${MYSQL_USER}'@'%';
FLUSH PRIVILEGES;
SQL
