CREATE TABLE IF NOT EXISTS saved_scenarios (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    plan_json TEXT NOT NULL,
    created_at VARCHAR(40) NOT NULL
);
