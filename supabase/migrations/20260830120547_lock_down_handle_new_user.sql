-- handle_new_user() solo debe ejecutarse desde el trigger de auth.users,
-- nunca como RPC desde el cliente. El trigger sigue funcionando sin este permiso.
revoke execute on function public.handle_new_user() from anon, authenticated, public;
revoke execute on function public.set_updated_at() from anon, authenticated, public;
revoke execute on function public.enforce_cat_limit() from anon, authenticated, public;
