import { createClient } from '@supabase/supabase-js';

const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;

export const supabase = url && anonKey ? createClient(url, anonKey) : null;

export type Schedule = {
  id: string;
  name: string;
  time: string;
  playlist_url: string;
  enabled: boolean;
  timezone: string;
  created_at: string;
  updated_at: string;
  last_triggered_at?: string | null;
  last_status?: string | null;
  last_error?: string | null;
};
