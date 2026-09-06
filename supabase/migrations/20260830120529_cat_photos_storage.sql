-- ═══════════════════════════════════════════════════════════
-- Almacenamiento de fotos de los gatos
-- Bucket privado. Ruta: {user_id}/{cat_local_id}.jpg
-- Cada usuario solo accede a su propia carpeta.
-- ═══════════════════════════════════════════════════════════

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('cat-photos', 'cat-photos', false, 2097152, array['image/jpeg','image/png','image/webp'])
on conflict (id) do update
  set file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

create policy "cat_photos_select_own" on storage.objects
  for select to authenticated
  using (bucket_id = 'cat-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy "cat_photos_insert_own" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'cat-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy "cat_photos_update_own" on storage.objects
  for update to authenticated
  using (bucket_id = 'cat-photos' and (storage.foldername(name))[1] = (select auth.uid())::text)
  with check (bucket_id = 'cat-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy "cat_photos_delete_own" on storage.objects
  for delete to authenticated
  using (bucket_id = 'cat-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);
