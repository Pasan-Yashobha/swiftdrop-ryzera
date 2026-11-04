-- Ensure demo accounts exist and have the verified BCrypt hash for 'Password123!'
-- Hash: $2a$10$ysgTy8XDqwZGCuteN3bfXumltdaM9AMm4bYkfwo7AB4pZiKeAUPli
--
-- Uses UPDATE + INSERT WHERE NOT EXISTS instead of ON CONFLICT DO UPDATE so this
-- migration runs on both PostgreSQL (production) and H2 (test suite).

UPDATE users SET password_hash = '$2a$10$ysgTy8XDqwZGCuteN3bfXumltdaM9AMm4bYkfwo7AB4pZiKeAUPli'
WHERE email = 'admin@swiftdrop.com';

INSERT INTO users (
    id, full_name, email, phone, password_hash, google_id, avatar_url, role,
    failed_attempts, is_locked, locked_until, deleted_at, created_at, updated_at, version
)
SELECT
    'a0000000-0000-0000-0000-000000000001',
    'System Admin',
    'admin@swiftdrop.com',
    '+94770000001',
    '$2a$10$ysgTy8XDqwZGCuteN3bfXumltdaM9AMm4bYkfwo7AB4pZiKeAUPli',
    NULL, NULL, 'ADMIN', 0, FALSE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@swiftdrop.com');


UPDATE users SET password_hash = '$2a$10$ysgTy8XDqwZGCuteN3bfXumltdaM9AMm4bYkfwo7AB4pZiKeAUPli'
WHERE email = 'merchant@swiftdrop.com';

INSERT INTO users (
    id, full_name, email, phone, password_hash, google_id, avatar_url, role,
    failed_attempts, is_locked, locked_until, deleted_at, created_at, updated_at, version
)
SELECT
    'a0000000-0000-0000-0000-000000000002',
    'Apex Goods (Merchant)',
    'merchant@swiftdrop.com',
    '+94770000002',
    '$2a$10$ysgTy8XDqwZGCuteN3bfXumltdaM9AMm4bYkfwo7AB4pZiKeAUPli',
    NULL, NULL, 'MERCHANT', 0, FALSE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'merchant@swiftdrop.com');


UPDATE users SET password_hash = '$2a$10$ysgTy8XDqwZGCuteN3bfXumltdaM9AMm4bYkfwo7AB4pZiKeAUPli'
WHERE email = 'courier@swiftdrop.com';

INSERT INTO users (
    id, full_name, email, phone, password_hash, google_id, avatar_url, role,
    failed_attempts, is_locked, locked_until, deleted_at, created_at, updated_at, version
)
SELECT
    'a0000000-0000-0000-0000-000000000003',
    'Swift Courier Driver',
    'courier@swiftdrop.com',
    '+94770000003',
    '$2a$10$ysgTy8XDqwZGCuteN3bfXumltdaM9AMm4bYkfwo7AB4pZiKeAUPli',
    NULL, NULL, 'COURIER', 0, FALSE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'courier@swiftdrop.com');


UPDATE users SET password_hash = '$2a$10$ysgTy8XDqwZGCuteN3bfXumltdaM9AMm4bYkfwo7AB4pZiKeAUPli'
WHERE email = 'customer@swiftdrop.com';

INSERT INTO users (
    id, full_name, email, phone, password_hash, google_id, avatar_url, role,
    failed_attempts, is_locked, locked_until, deleted_at, created_at, updated_at, version
)
SELECT
    'a0000000-0000-0000-0000-000000000004',
    'Jane Customer',
    'customer@swiftdrop.com',
    '+94770000004',
    '$2a$10$ysgTy8XDqwZGCuteN3bfXumltdaM9AMm4bYkfwo7AB4pZiKeAUPli',
    NULL, NULL, 'CUSTOMER', 0, FALSE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'customer@swiftdrop.com');
