-- One Postgres instance, one database per service. Each service owns its schema;
-- neither reads the other's tables. Split to separate instances in a real deployment.
CREATE DATABASE insights OWNER claims;
