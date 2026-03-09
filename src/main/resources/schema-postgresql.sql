CREATE TABLE IF NOT EXISTS generation_requests (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    template VARCHAR(255) NOT NULL,
    database BOOLEAN NOT NULL,
    rest_api BOOLEAN NOT NULL,
    security BOOLEAN NOT NULL,
    messaging BOOLEAN NOT NULL,
    deployment_target VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_generation_requests_name UNIQUE (name)
);
