create table if not exists public.phone_commands (
  id uuid primary key default gen_random_uuid(),
  command text not null,
  action text not null check (action in ('OPEN_YOUTUBE','WHATSAPP_MESSAGE','INSTAGRAM_BIO')),
  target text,
  payload text,
  status text not null default 'pending' check (status in ('pending','running','completed','failed')),
  result text,
  created_at timestamptz not null default now(),
  started_at timestamptz,
  completed_at timestamptz
);

create index if not exists phone_commands_status_created_idx
  on public.phone_commands(status, created_at);

alter table public.phone_commands enable row level security;

drop policy if exists "phone_commands_public_select" on public.phone_commands;
drop policy if exists "phone_commands_public_insert" on public.phone_commands;
drop policy if exists "phone_commands_public_update" on public.phone_commands;

create policy "phone_commands_public_select"
  on public.phone_commands for select
  using (true);

create policy "phone_commands_public_insert"
  on public.phone_commands for insert
  with check (true);

create policy "phone_commands_public_update"
  on public.phone_commands for update
  using (true)
  with check (true);
