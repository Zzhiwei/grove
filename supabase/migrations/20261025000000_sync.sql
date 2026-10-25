-- Grove cloud sync. Paste into Supabase Dashboard › SQL Editor and run once.

create table public.sessions (
  id         uuid primary key,  -- generated on the device, so re-uploads are idempotent
  user_id    uuid not null default auth.uid() references auth.users on delete cascade,
  tag        text not null check (length(tag) between 1 and 100),
  started_at timestamptz not null,
  seconds    double precision not null check (seconds >= 0),
  created_at timestamptz not null default now()
);
create index sessions_user_started_idx on public.sessions (user_id, started_at);

create table public.tags (
  user_id    uuid not null default auth.uid() references auth.users on delete cascade,
  name       text not null check (length(name) between 1 and 100),
  deleted    boolean not null default false,  -- tombstone so removals reach other devices
  updated_at timestamptz not null default now(),
  primary key (user_id, name)
);

alter table public.sessions enable row level security;
alter table public.tags enable row level security;

create policy "own sessions: read" on public.sessions for select to authenticated
  using ((select auth.uid()) = user_id);
create policy "own sessions: insert" on public.sessions for insert to authenticated
  with check ((select auth.uid()) = user_id);

create policy "own tags: read" on public.tags for select to authenticated
  using ((select auth.uid()) = user_id);
create policy "own tags: insert" on public.tags for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy "own tags: update" on public.tags for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

revoke all on public.sessions, public.tags from anon, authenticated;
grant select, insert on public.sessions to authenticated;
grant select, insert, update on public.tags to authenticated;
