-- Seed demo accounts for all 4 roles with default password 'Password123!'
-- BCrypt cost 10 hash for 'Password123!': $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG
--
-- Uses INSERT ... SELECT ... WHERE NOT EXISTS instead of ON CONFLICT so this
-- migration runs on both PostgreSQL (production) and H2 (test suite).

INSERT INTO users (
    id, full_name, email, phone, password_hash, google_id, avatar_url, role,
    failed_attempts, is_locked, locked_until, deleted_at, created_at, updated_at, version
)
SELECT
    'a0000000-0000-0000-0000-000000000001',
    'System Admin',
    'admin@swiftdrop.com',
    '+94770000001',
    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
    NULL, NULL, 'ADMIN', 0, FALSE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@swiftdrop.com');

INSERT INTO users (
    id, full_name, email, phone, password_hash, google_id, avatar_url, role,
    failed_attempts, is_locked, locked_until, deleted_at, created_at, updated_at, version
)
SELECT
    'a0000000-0000-0000-0000-000000000002',
    'Apex Goods (Merchant)',
    'merchant@swiftdrop.com',
    '+94770000002',
    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
    NULL, NULL, 'MERCHANT', 0, FALSE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'merchant@swiftdrop.com');

INSERT INTO users (
    id, full_name, email, phone, password_hash, google_id, avatar_url, role,
    failed_attempts, is_locked, locked_until, deleted_at, created_at, updated_at, version
)
SELECT
    'a0000000-0000-0000-0000-000000000003',
    'Swift Courier Driver',
    'courier@swiftdrop.com',
    '+94770000003',
    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
    NULL, NULL, 'COURIER', 0, FALSE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'courier@swiftdrop.com');

INSERT INTO users (
    id, full_name, email, phone, password_hash, google_id, avatar_url, role,
    failed_attempts, is_locked, locked_until, deleted_at, created_at, updated_at, version
)
SELECT
    'a0000000-0000-0000-0000-000000000004',
    'Jane Customer',
    'customer@swiftdrop.com',
    '+94770000004',
    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
    NULL, NULL, 'CUSTOMER', 0, FALSE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'customer@swiftdrop.com');
