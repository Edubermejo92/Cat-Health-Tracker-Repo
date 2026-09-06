-- ═══════════════════════════════════════════════════════════
-- Seguridad por filas (RLS)
-- Sin estas políticas, nadie puede leer ni escribir nada.
-- Con ellas, cada usuario autenticado solo alcanza sus filas.
-- ═══════════════════════════════════════════════════════════

alter table public.profiles  enable row level security;
alter table public.cats      enable row level security;
alter table public.records   enable row level security;
alter table public.reminders enable row level security;

-- ── PERFILES ──
create policy "profiles_select_own" on public.profiles
  for select to authenticated using ((select auth.uid()) = id);
create policy "profiles_insert_own" on public.profiles
  for insert to authenticated with check ((select auth.uid()) = id);
create policy "profiles_update_own" on public.profiles
  for update to authenticated using ((select auth.uid()) = id) with check ((select auth.uid()) = id);
create policy "profiles_delete_own" on public.profiles
  for delete to authenticated using ((select auth.uid()) = id);

-- ── GATOS ──
create policy "cats_select_own" on public.cats
  for select to authenticated using ((select auth.uid()) = user_id);
create policy "cats_insert_own" on public.cats
  for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "cats_update_own" on public.cats
  for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "cats_delete_own" on public.cats
  for delete to authenticated using ((select auth.uid()) = user_id);

-- ── REGISTROS ──
-- Además de pertenecer al usuario, el gato referenciado debe ser suyo.
create policy "records_select_own" on public.records
  for select to authenticated using ((select auth.uid()) = user_id);
create policy "records_insert_own" on public.records
  for insert to authenticated with check (
    (select auth.uid()) = user_id
    and exists (select 1 from public.cats c where c.id = cat_id and c.user_id = (select auth.uid()))
  );
create policy "records_update_own" on public.records
  for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "records_delete_own" on public.records
  for delete to authenticated using ((select auth.uid()) = user_id);

-- ── RECORDATORIOS ──
create policy "reminders_select_own" on public.reminders
  for select to authenticated using ((select auth.uid()) = user_id);
create policy "reminders_insert_own" on public.reminders
  for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "reminders_update_own" on public.reminders
  for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "reminders_delete_own" on public.reminders
  for delete to authenticated using ((select auth.uid()) = user_id);
