// delete-account — borra la cuenta del usuario que llama y todos sus datos.
//
// Lo exige Google Play: quien crea una cuenta dentro de la app tiene que poder
// borrarla desde la propia app. El cliente no puede hacerlo solo (borrar de
// auth.users necesita la clave secreta), asi que lo hace esta funcion.
//
// Reglas:
//  - Solo POST, con el token de sesion del usuario en "Authorization: Bearer"
//    y {"confirm": true} en el cuerpo.
//  - El token se verifica aqui, contra el servidor de Auth. La comprobacion
//    automatica de la plataforma (verify_jwt) no sirve con las claves nuevas
//    sb_publishable_/sb_secret_, por eso se despliega con verify_jwt = false.
//  - La clave secreta solo existe en el entorno de la funcion; la app nunca
//    la ve.
//  - Se borran las filas del usuario en las tablas de la app y despues el
//    usuario. Las cuatro tablas tienen ON DELETE CASCADE hacia auth.users,
//    asi que el borrado explicito es una segunda red, no la unica.
//
// Respuestas: 200 {ok:true, deleted:{...}} o {ok:false, error:<codigo>}
// con 401 (no_token, invalid_token), 405, 400 (no_confirm) o 500
// (config, db_error, auth_error).

import { createClient } from "npm:@supabase/supabase-js@2";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "Content-Type": "application/json" },
  });
}

/** Clave secreta del proyecto: la nueva (sb_secret_) o, si no hay, la antigua service_role. */
function secretKey(): string | null {
  try {
    const keys = JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS") ?? "{}");
    if (keys && typeof keys.default === "string" && keys.default) return keys.default;
  } catch (_) { /* sin claves nuevas */ }
  return Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? null;
}

// Tabla y columna que apunta al usuario. profiles usa su propia id.
const TABLES: Array<[string, string]> = [
  ["matches", "user_id"],
  ["players", "user_id"],
  ["settings", "user_id"],
  ["profiles", "id"],
];

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json(405, { ok: false, error: "method_not_allowed" });

  const auth = req.headers.get("Authorization") ?? "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
  if (!token) return json(401, { ok: false, error: "no_token" });

  let body: { confirm?: unknown } = {};
  try { body = await req.json(); } catch (_) { /* cuerpo vacio */ }
  if (body.confirm !== true) return json(400, { ok: false, error: "no_confirm" });

  const url = Deno.env.get("SUPABASE_URL");
  const key = secretKey();
  if (!url || !key) return json(500, { ok: false, error: "config" });

  const admin = createClient(url, key, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  // Quien llama: el servidor de Auth comprueba firma, caducidad y que el
  // usuario sigue existiendo.
  const { data: who, error: whoErr } = await admin.auth.getUser(token);
  if (whoErr || !who?.user?.id) return json(401, { ok: false, error: "invalid_token" });
  const uid = who.user.id;

  const deleted: Record<string, number> = {};
  for (const [table, column] of TABLES) {
    const { error, count } = await admin.from(table).delete({ count: "exact" }).eq(column, uid);
    if (error) {
      console.error(`delete-account ${uid.slice(0, 6)}: ${table}: ${error.message}`);
      return json(500, { ok: false, error: "db_error", table });
    }
    deleted[table] = count ?? 0;
  }

  const { error: delErr } = await admin.auth.admin.deleteUser(uid);
  if (delErr) {
    console.error(`delete-account ${uid.slice(0, 6)}: auth: ${delErr.message}`);
    return json(500, { ok: false, error: "auth_error" });
  }

  console.log(`delete-account ${uid.slice(0, 6)}: ok ${JSON.stringify(deleted)}`);
  return json(200, { ok: true, deleted });
});
