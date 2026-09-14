alter table public.schedules
  add column if not exists scheduled_date date;

comment on column public.schedules.scheduled_date is 'Optional one-time playback date in the schedule timezone. NULL means repeat daily.';

create index if not exists schedules_scheduled_date_idx on public.schedules (scheduled_date);