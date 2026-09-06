-- ═══════════════════════════════════════════════════════════
-- Disparador periódico de la Edge Function send-reminder-pushes
--
-- pg_cron invoca esta función cada 5 minutos. Ella lee el secreto
-- compartido de Vault en el momento de ejecutarse (nunca queda escrito
-- en ningún archivo) y hace una petición HTTP asíncrona (pg_net) a la
-- Edge Function, que es la que de verdad decide qué avisos tocan y los
-- envía.
--
-- Si reproduces esto en otro proyecto, cambia v_url por
-- https://<tu-ref>.supabase.co/functions/v1/send-reminder-pushes antes
-- de aplicar esta migración.
-- ═══════════════════════════════════════════════════════════

create or replace function public.trigger_send_reminder_pushes()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_secret text := public.get_secret('cron_secret');
  v_url    text := 'https://vwdnipvfjqeeezuhjstk.supabase.co/functions/v1/send-reminder-pushes';
begin
  if v_secret is null then
    raise warning 'trigger_send_reminder_pushes: falta cron_secret en Vault, no se invoca la funcion';
    return;
  end if;
  perform net.http_post(
    url     := v_url,
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-cron-secret', v_secret
    ),
    body    := '{}'::jsonb,
    timeout_milliseconds := 20000
  );
end;
$$;

revoke execute on function public.trigger_send_reminder_pushes() from anon, authenticated, public;

select cron.schedule(
  'send-reminder-pushes',
  '*/5 * * * *',
  $$select public.trigger_send_reminder_pushes()$$
);
