-- Ensure demo accounts exist and have the verified BCrypt hash for 'Password123!'
-- Hash: $2a$10$ysgTy8XDqwZGCuteN3bfXumltdaM9AMm4bYkfwo7AB4pZiKeAUPli

INSERT INTO users (
    id, full_name, email, phone, password_hash, google_id, avatar_url, role,
    failed_attempts, is_locked, locked_until, deleted_at, created_at, updated_at, version
) VALUES
(
    'a0000000-0000-0000-0000-000000000001',
    'System Admin',
    'admin@swiftdrop.com',
    '+94770000001',
    '$2a$10$ysgTy8XDqwZGCuteN3bfXumltdaM9AMm4bYkfwo7AB4pZiKeAUPli',
    NULL, NULL, 'ADMIN', 0, FALSE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
),
(
    'a0000000-0000-0000-0000-000000000002',
    'Apex Goods (Merchant)',
    'merchant@swiftdrop.com',
    '+94770000002',
    '$2a$10$ysgTy8XDqwZGCuteN3bfXumltdaM9AMm4bYkfwo7AB4pZiKeAUPli',
    NULL, NULL, 'MERCHANT', 0, FALSE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
),
(
    'a0000000-0000-0000-0000-000000000003',
    'Swift Courier Driver',
    'courier@swiftdrop.com',
    '+94770000003',
    '$2a$10$ysgTy8XDqwZGCuteN3bfXumltdaM9AMm4bYkfwo7AB4pZiKeAUPli',
    NULL, NULL, 'COURIER', 0, FALSE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
),
(
    'a0000000-0000-0000-0000-000000000004',
    'Jane Customer',
    'customer@swiftdrop.com',
    '+94770000004',
    '$2a$10$ysgTy8XDqwZGCuteN3bfXumltdaM9AMm4bYkfwo7AB4pZiKeAUPli',
    NULL, NULL, 'CUSTOMER', 0, FALSE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
)
ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash;

