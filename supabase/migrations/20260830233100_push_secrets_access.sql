-- ═══════════════════════════════════════════════════════════
-- Acceso a Supabase Vault para la Edge Function
--
-- Las claves VAPID y el secreto del cron NO viven en ningún archivo del
-- repositorio: se guardan cifradas en vault.secrets (ya instalado en este
-- proyecto) y esta función es la única puerta para leerlas. anon y
-- authenticated tienen el EXECUTE revocado explícitamente; service_role
-- lo conserva (es el que usa la Edge Function), igual que en el resto de
-- funciones sensibles de este proyecto.
--
-- Esta migración solo crea la función. Los valores en sí (vapid_public_key,
-- vapid_private_key, cron_secret) se insertan una vez, a mano, con
-- `select vault.create_secret(valor, nombre, descripcion);` — nunca desde
-- un archivo versionado. Ver el README, sección "Notificaciones push",
-- para los tres nombres exactos que espera la Edge Function.
-- ═══════════════════════════════════════════════════════════

create or replace function public.get_secret(secret_name text)
returns text
language sql
security definer
set search_path = ''
stable
as $$
  select decrypted_secret from vault.decrypted_secrets where name = secret_name limit 1;
$$;

revoke execute on function public.get_secret(text) from anon, authenticated, public;
