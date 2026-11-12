-- Identity Service schema (database: identity_db, owner: identity_svc).
-- Written to run unchanged on PostgreSQL 16 and H2 in PostgreSQL mode:
-- standard data types, application-generated UUIDs, indexes and constraints.

create table users (
    id               uuid                     primary key,
    full_name        varchar(100)             not null,
    email            varchar(254)             not null,
    phone            varchar(32),
    password_hash    varchar(255),
    google_id        varchar(255),
    avatar_url       varchar(500),
    role             varchar(20)              not null default 'CUSTOMER',
    failed_attempts  integer                  not null default 0,
    is_locked        boolean                  not null default false,
    locked_until     timestamp with time zone,
    deleted_at       timestamp with time zone,
    created_at       timestamp with time zone not null,
    updated_at       timestamp with time zone not null,
    version          bigint                   not null default 0,
    constraint uq_users_email unique (email),
    constraint uq_users_google_id unique (google_id),
    constraint ck_users_role check (role in ('CUSTOMER', 'MERCHANT', 'COURIER', 'ADMIN', 'DISPATCHER'))
);
create index ix_users_email on users (email);
create index ix_users_role on users (role);
create index ix_users_deleted_at on users (deleted_at);

create table refresh_tokens (
    id                uuid                     primary key,
    user_id           uuid                     not null references users (id) on delete cascade,
    family_id         uuid                     not null,
    token_hash        varchar(64)              not null,
    is_revoked        boolean                  not null default false,
    revoked_at        timestamp with time zone,
    family_created_at timestamp with time zone not null,
    expires_at        timestamp with time zone not null,
    created_at        timestamp with time zone not null,
    constraint uq_refresh_tokens_hash unique (token_hash)
);
create index ix_refresh_tokens_user on refresh_tokens (user_id);
create index ix_refresh_tokens_family on refresh_tokens (family_id);
create index ix_refresh_tokens_hash on refresh_tokens (token_hash);

create table login_attempts (
    id           uuid                     primary key,
    email        varchar(254)             not null,
    ip_address   varchar(45)              not null,
    success      boolean                  not null,
    attempted_at timestamp with time zone not null
);
create index ix_login_attempts_email on login_attempts (email, attempted_at);
create index ix_login_attempts_ip on login_attempts (ip_address, attempted_at);
