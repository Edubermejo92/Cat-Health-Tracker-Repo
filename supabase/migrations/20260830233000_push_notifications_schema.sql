-- ═══════════════════════════════════════════════════════════
-- Notificaciones push en segundo plano
--
-- Hasta ahora las alarmas solo sonaban con la pestaña abierta (un
-- setInterval en el propio JS). Para que lleguen con la app cerrada hace
-- falta que algo fuera del navegador sepa la hora de cada recordatorio y
-- se lo mande al dispositivo: eso es exactamente lo que monta este bloque.
--
-- push_subscriptions: una fila por dispositivo suscrito (Web Push), con su
--   endpoint del navegador, sus claves de cifrado y la zona horaria que
--   reportó al suscribirse (para saber "las 8 de la mañana" de verdad).
-- push_log: qué avisos ya se han enviado, para no duplicar un envío si el
--   cron pasa varias veces por la misma ventana horaria.
-- ═══════════════════════════════════════════════════════════

create extension if not exists pg_cron;
create extension if not exists pg_net;

create table public.push_subscriptions (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references auth.users(id) on delete cascade,
  endpoint   text not null unique,
  p256dh     text not null,
  auth       text not null,
  tz         text not null default 'UTC',
  user_agent text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
comment on table public.push_subscriptions is 'Un dispositivo suscrito a Web Push por usuario. El endpoint lo asigna el navegador y es único.';
comment on column public.push_subscriptions.tz is 'Zona horaria IANA (Intl.DateTimeFormat().resolvedOptions().timeZone) capturada al suscribirse.';

alter table public.push_subscriptions enable row level security;

create policy "push_subs_select_own" on public.push_subscriptions
  for select to authenticated using ((select auth.uid()) = user_id);
create policy "push_subs_insert_own" on public.push_subscriptions
  for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "push_subs_update_own" on public.push_subscriptions
  for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "push_subs_delete_own" on public.push_subscriptions
  for delete to authenticated using ((select auth.uid()) = user_id);

create trigger push_subscriptions_updated_at
  before update on public.push_subscriptions
  for each row execute function public.set_updated_at();

create index push_subs_user_idx on public.push_subscriptions (user_id);

-- push_log: solo lo toca la Edge Function con la service role key, que
-- ignora RLS. Sin políticas, ni el propio dueño puede leerlo desde el
-- cliente: no aporta nada a la app y sería una superficie de fuga más.
create table public.push_log (
  subscription_id uuid not null references public.push_subscriptions(id) on delete cascade,
  dedupe_key      text not null,
  sent_at         timestamptz not null default now(),
  primary key (subscription_id, dedupe_key)
);
comment on table public.push_log is 'Envíos ya realizados, para no mandar el mismo aviso dos veces si el cron solapa su ventana horaria.';
alter table public.push_log enable row level security;

-- Limpieza automática: sin esto push_log crecería para siempre.
create index push_log_sent_at_idx on public.push_log (sent_at);
