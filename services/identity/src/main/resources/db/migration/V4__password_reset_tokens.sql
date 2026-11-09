-- Password reset recovery tokens table.
-- Supports SHA-256 token hashes, 15-minute expiration, and single-use invalidation.
-- Written to run identically on PostgreSQL 16 and H2 test database.

create table password_reset_tokens (
    id         uuid                     primary key,
    user_id    uuid                     not null references users (id) on delete cascade,
    token_hash varchar(64)              not null,
    expires_at timestamp with time zone not null,
    is_used    boolean                  not null default false,
    created_at timestamp with time zone not null,
    constraint uq_password_reset_tokens_hash unique (token_hash)
);

create index ix_password_reset_tokens_user on password_reset_tokens (user_id);
create index ix_password_reset_tokens_hash on password_reset_tokens (token_hash);

