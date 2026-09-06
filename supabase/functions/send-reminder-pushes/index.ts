// Cat Health Tracker — envío de notificaciones push en segundo plano
//
// pg_cron invoca esta función cada 5 minutos (ver la migración
// push_cron_schedule.sql). Su trabajo: mirar qué recordatorios tocan
// ahora mismo para cada dispositivo suscrito —en SU zona horaria, no la
// del servidor— y mandarles un Web Push. Es lo que hace que un aviso
// llegue con el móvil bloqueado o la app cerrada, que es exactamente lo
// que una alarma dentro de la pestaña del navegador no puede lograr.
//
// Autenticación: no se usa un JWT de Supabase (por eso se despliega con
// verify_jwt=false), sino un secreto compartido en la cabecera
// x-cron-secret, comparado contra el valor guardado en Vault. Es el caso
// que la propia documentación de Supabase contempla para desactivar la
// verificación por defecto: autenticación propia dentro de la función.
//
// La lógica de "¿le toca hoy, a esta hora?" (remOccursOn / remTimes) es
// una réplica deliberada de la que vive en index.html. Si cambias una,
// cambia la otra — están comentadas exactamente en paralelo para que sea
// fácil verlo.

import webpush from "npm:web-push@3.6.7";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

function restHeaders(extra?: Record<string, string>) {
  return {
    apikey: SERVICE_KEY,
    Authorization: "Bearer " + SERVICE_KEY,
    "Content-Type": "application/json",
    ...extra,
  };
}

async function rest(path: string, init?: RequestInit) {
  const res = await fetch(SUPABASE_URL + path, {
    ...init,
    headers: { ...restHeaders(), ...(init?.headers as Record<string, string> | undefined) },
  });
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(`REST ${init?.method || "GET"} ${path} -> ${res.status}: ${body.slice(0, 300)}`);
  }
  return res;
}

async function getSecret(name: string): Promise<string | null> {
  const res = await rest("/rest/v1/rpc/get_secret", {
    method: "POST",
    body: JSON.stringify({ secret_name: name }),
  });
  const data = await res.json();
  return typeof data === "string" ? data : null;
}

/* ═══════════════════════════════════════
   Fecha y hora en la zona horaria de cada suscripción
   (réplica del "day/month/weekday" que en el cliente da
   directamente el objeto Date local del navegador)
   ═══════════════════════════════════════ */
type YMD = { y: number; m: number; d: number };
type NowParts = YMD & { hh: number; mm: number; weekday: number; iso: string };

const WEEKDAY_INDEX: Record<string, number> = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 };

function safeTz(tz: string | null | undefined): string {
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: tz || "UTC" });
    return tz || "UTC";
  } catch {
    return "UTC";
  }
}

function nowInTz(tz: string, at: Date = new Date()): NowParts {
  const dtf = new Intl.DateTimeFormat("en-US", {
    timeZone: tz,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
    weekday: "short",
  });
  const parts: Record<string, string> = {};
  for (const p of dtf.formatToParts(at)) parts[p.type] = p.value;
  const y = Number(parts.year), m = Number(parts.month), d = Number(parts.day);
  return {
    y, m, d,
    hh: Number(parts.hour),
    mm: Number(parts.minute),
    weekday: WEEKDAY_INDEX[parts.weekday] ?? 0,
    iso: `${parts.year}-${parts.month}-${parts.day}`,
  };
}

function isoToYMD(iso: string): YMD {
  const [y, m, d] = iso.split("-").map(Number);
  return { y, m, d };
}
function ymdToUTCms(p: YMD) { return Date.UTC(p.y, p.m - 1, p.d); }
function daysBetween(a: YMD, b: YMD) { return Math.round((ymdToUTCms(b) - ymdToUTCms(a)) / 86400000); }
function daysInMonth(y: number, m: number) { return new Date(Date.UTC(y, m, 0)).getUTCDate(); }

/* ═══════════════════════════════════════
   remOccursOn / remTimes
   Debe leerse en paralelo a las funciones homónimas de index.html.
   ═══════════════════════════════════════ */
interface ReminderRow {
  id: string; user_id: string; cat_id: string | null;
  icon: string; title: string; freq: string;
  time_of_day: string; times: string[]; weekdays: number[];
  every_n: number; start_date: string | null; alarm: number; enabled: boolean;
}

function remOccursOn(r: ReminderRow, now: NowParts): boolean {
  if (!r.enabled) return false;
  const anchor = r.start_date ? isoToYMD(r.start_date) : null;
  switch (r.freq) {
    case "daily": case "times": return true;
    case "weekly":
      return (r.weekdays && r.weekdays.length) ? r.weekdays.includes(now.weekday) : true;
    case "everyn": {
      if (!anchor) return true;
      const n = Math.max(1, r.every_n || 1);
      const diff = daysBetween(anchor, now);
      return diff >= 0 && diff % n === 0;
    }
    case "monthly": {
      if (!anchor) return true;
      const last = daysInMonth(now.y, now.m);
      return now.d === Math.min(anchor.d, last);
    }
    case "yearly":
      return !!anchor && now.m === anchor.m && now.d === anchor.d;
    case "once":
      return !!anchor && now.y === anchor.y && now.m === anchor.m && now.d === anchor.d;
    default: return true;
  }
}
function remTimes(r: ReminderRow): string[] {
  if (r.freq === "times" && Array.isArray(r.times) && r.times.length) return [...r.times].sort();
  return [(r.time_of_day || "09:00").slice(0, 5)];
}

/* Ventana de tolerancia: el cron pasa cada 5 min, así que 15 min cubre
   hasta 2 pasadas perdidas antes de dar el aviso por caducado. */
const WINDOW_MINUTES = 15;

interface Subscription {
  id: string; user_id: string; endpoint: string; p256dh: string; auth: string; tz: string;
}

Deno.serve(async (req) => {
  try {
    const expected = await getSecret("cron_secret");
    const provided = req.headers.get("x-cron-secret");
    if (!expected || !provided || provided !== expected) {
      return new Response(JSON.stringify({ error: "unauthorized" }), { status: 401 });
    }

    const [vapidPublic, vapidPrivate] = await Promise.all([
      getSecret("vapid_public_key"),
      getSecret("vapid_private_key"),
    ]);
    if (!vapidPublic || !vapidPrivate) {
      return new Response(JSON.stringify({ error: "vapid keys missing" }), { status: 500 });
    }
    webpush.setVapidDetails("mailto:soporte@cathealthtrackerapp.netlify.app", vapidPublic, vapidPrivate);

    const [remindersRes, subsRes] = await Promise.all([
      rest("/rest/v1/reminders?enabled=eq.true&deleted_at=is.null&select=id,user_id,cat_id,icon,title,freq,time_of_day,times,weekdays,every_n,start_date,alarm,enabled"),
      rest("/rest/v1/push_subscriptions?select=id,user_id,endpoint,p256dh,auth,tz"),
    ]);
    const reminders: ReminderRow[] = await remindersRes.json();
    const subs: Subscription[] = await subsRes.json();

    const remindersByUser = new Map<string, ReminderRow[]>();
    for (const r of reminders) {
      if (!remindersByUser.has(r.user_id)) remindersByUser.set(r.user_id, []);
      remindersByUser.get(r.user_id)!.push(r);
    }

    let sent = 0, skippedDup = 0, pruned = 0, errors = 0;

    for (const sub of subs) {
      const userReminders = remindersByUser.get(sub.user_id);
      if (!userReminders || !userReminders.length) continue;

      const tz = safeTz(sub.tz);
      const now = nowInTz(tz);
      const nowMin = now.hh * 60 + now.mm;

      for (const r of userReminders) {
        if (r.alarm === 0) continue;                 // el usuario apagó la alarma para este recordatorio
        if (!remOccursOn(r, now)) continue;

        for (const time of remTimes(r)) {
          const [hh, mm] = time.split(":").map(Number);
          const delta = nowMin - (hh * 60 + mm);
          if (delta < 0 || delta > WINDOW_MINUTES) continue;

          const dedupeKey = `${r.id}|${now.iso}|${time}`;

          // Inserta o, si ya existe, no hace nada y no devuelve fila:
          // así el envío real solo ocurre una vez por recordatorio/día/hora,
          // aunque el cron pase varias veces dentro de la ventana.
          const insRes = await rest(
            "/rest/v1/push_log?on_conflict=subscription_id,dedupe_key",
            {
              method: "POST",
              headers: { Prefer: "resolution=ignore-duplicates,return=representation" },
              body: JSON.stringify([{ subscription_id: sub.id, dedupe_key: dedupeKey }]),
            },
          );
          const inserted = await insRes.json().catch(() => []);
          if (!Array.isArray(inserted) || inserted.length === 0) { skippedDup++; continue; }

          try {
            await webpush.sendNotification(
              { endpoint: sub.endpoint, keys: { p256dh: sub.p256dh, auth: sub.auth } },
              JSON.stringify({
                title: `${r.icon || "🔔"} ${r.title}`,
                body: time,
                tag: dedupeKey,
              }),
            );
            sent++;
          } catch (err) {
            const status = (err as { statusCode?: number }).statusCode;
            if (status === 404 || status === 410) {
              // El navegador dio de baja esta suscripción (desinstaló, borró datos...).
              await rest(`/rest/v1/push_subscriptions?id=eq.${sub.id}`, { method: "DELETE" }).catch(() => {});
              pruned++;
            } else {
              errors++;
              console.error("push error", sub.id, String(err));
            }
          }
        }
      }
    }

    // Limpieza: sin esto push_log crecería para siempre.
    const cutoff = new Date(Date.now() - 3 * 86400000).toISOString();
    await rest(`/rest/v1/push_log?sent_at=lt.${encodeURIComponent(cutoff)}`, { method: "DELETE" }).catch(() => {});

    return new Response(
      JSON.stringify({ ok: true, subs: subs.length, reminders: reminders.length, sent, skippedDup, pruned, errors }),
      { headers: { "Content-Type": "application/json" } },
    );
  } catch (err) {
    console.error("send-reminder-pushes fatal", String(err));
    return new Response(JSON.stringify({ error: String(err) }), { status: 500 });
  }
});
