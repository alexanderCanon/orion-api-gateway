CREATE TABLE roles (
    role_id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE permissions (
    permission_id UUID PRIMARY KEY,
    role_id UUID NOT NULL REFERENCES roles(role_id),
    permission VARCHAR(255) NOT NULL
);

CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    status VARCHAR(50) NOT NULL,
    role_id UUID NOT NULL REFERENCES roles(role_id),
    organizer_id UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
