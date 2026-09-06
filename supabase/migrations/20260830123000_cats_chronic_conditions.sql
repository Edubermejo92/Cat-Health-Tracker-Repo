-- Problemas de salud crónicos declarados en el alta guiada del gato.
-- Se guardan como texto libre para admitir la opción "Otros".
alter table public.cats
  add column if not exists conditions text[] not null default '{}';

comment on column public.cats.conditions is
  'Problemas crónicos: claves conocidas (renal, urinario, asma...) y entradas libres añadidas por el usuario.';
