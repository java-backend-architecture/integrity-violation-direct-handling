create table if not exists users (
    id    uuid default gen_random_uuid(),
    name  text not null,
    email text not null,

    constraint pk_users primary key (id),
    constraint uq_users_name unique (name),
    constraint uq_users_email unique (email),

    constraint ck_users_name_no_outer_whitespace check (name !~ '^\s|\s$'),
    constraint ck_users_email_normalized check (email = lower(email) and email !~ '\s'),
    constraint ck_users_name_length check (length(name) between 2 and 5),
    constraint ck_users_email_length check (length(email) between 11 and 12)
    );