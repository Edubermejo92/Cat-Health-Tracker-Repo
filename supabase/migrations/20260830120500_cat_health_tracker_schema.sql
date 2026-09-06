-- ═══════════════════════════════════════════════════════════
-- Cat Health Tracker — esquema base
-- Cada usuario solo puede ver y modificar sus propios datos.
-- ═══════════════════════════════════════════════════════════

-- ── Utilidad: mantener updated_at al día ──
create or replace function public.set_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- ── PERFILES ──
create table public.profiles (
  id           uuid primary key references auth.users(id) on delete cascade,
  display_name text,
  lang         text not null default 'es' check (lang in ('es','en')),
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);
comment on table public.profiles is 'Perfil del usuario de la app, uno por cuenta de auth.';

-- ── GATOS ──
create table public.cats (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references auth.users(id) on delete cascade,
  local_id   text not null,
  name       text not null check (char_length(name) between 1 and 60),
  emoji      text not null default '🐈',
  photo_path text,
  breed      text,
  sex        text check (sex in ('M','H','?')),
  birth      date,
  weight     numeric(6,2) check (weight is null or weight >= 0),
  neutered   boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (user_id, local_id)
);
comment on column public.cats.local_id is 'Identificador que usa el dispositivo, para conciliar la sincronización.';
comment on column public.cats.deleted_at is 'Borrado lógico: permite propagar la baja a los demás dispositivos.';

-- ── REGISTROS ──
create table public.records (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references auth.users(id) on delete cascade,
  cat_id     uuid not null references public.cats(id) on delete cascade,
  local_id   text not null,
  type       text not null check (type in ('peso','veterinario','vacuna','antiparasitario','aseo','sintoma','nota')),
  date       date not null,
  weight     numeric(6,2) check (weight is null or weight >= 0),
  length_cm  numeric(6,1) check (length_cm is null or length_cm >= 0),
  bcs        smallint check (bcs is null or bcs between 1 and 9),
  severity   text check (severity is null or severity in ('leve','moderado','grave')),
  symptoms   text[] not null default '{}',
  notes      text check (notes is null or char_length(notes) <= 4000),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (user_id, local_id)
);

-- ── RECORDATORIOS ──
create table public.reminders (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  cat_id      uuid references public.cats(id) on delete set null,
  local_id    text not null,
  icon        text not null default '🔔',
  title       text not null check (char_length(title) between 1 and 80),
  i18n_key    text,
  freq        text not null default 'daily'
              check (freq in ('daily','times','weekly','everyn','monthly','yearly','once')),
  time_of_day time not null default '09:00',
  times       text[] not null default '{}',
  weekdays    smallint[] not null default '{}',
  every_n     integer not null default 1 check (every_n between 1 and 365),
  start_date  date,
  alarm       smallint not null default 2 check (alarm between 0 and 2),
  enabled     boolean not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  deleted_at  timestamptz,
  unique (user_id, local_id)
);

-- ── Índices para las consultas habituales ──
create index cats_user_idx        on public.cats (user_id) where deleted_at is null;
create index records_user_idx     on public.records (user_id, date desc) where deleted_at is null;
create index records_cat_idx      on public.records (cat_id, date desc) where deleted_at is null;
create index records_sync_idx     on public.records (user_id, updated_at desc);
create index cats_sync_idx        on public.cats (user_id, updated_at desc);
create index reminders_user_idx   on public.reminders (user_id) where deleted_at is null;
create index reminders_sync_idx   on public.reminders (user_id, updated_at desc);

-- ── Triggers de updated_at ──
create trigger profiles_updated_at  before update on public.profiles  for each row execute function public.set_updated_at();
create trigger cats_updated_at      before update on public.cats      for each row execute function public.set_updated_at();
create trigger records_updated_at   before update on public.records   for each row execute function public.set_updated_at();
create trigger reminders_updated_at before update on public.reminders for each row execute function public.set_updated_at();

-- ── Tope de 20 gatos por usuario ──
create or replace function public.enforce_cat_limit()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
declare
  n integer;
begin
  select count(*) into n
    from public.cats
   where user_id = new.user_id
     and deleted_at is null;
  if n >= 20 then
    raise exception 'Límite alcanzado: máximo 20 gatos por usuario'
      using errcode = 'check_violation';
  end if;
  return new;
end;
$$;

create trigger cats_limit_insert
  before insert on public.cats
  for each row when (new.deleted_at is null)
  execute function public.enforce_cat_limit();

-- ── Alta automática del perfil al registrarse ──
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, coalesce(new.raw_user_meta_data ->> 'display_name', split_part(new.email, '@', 1)))
  on conflict (id) do nothing;
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();
