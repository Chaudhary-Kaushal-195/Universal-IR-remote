import { createClient } from '@supabase/supabase-js'

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY
const cloudEnabled = import.meta.env.VITE_SUPABASE_ENABLED === 'true'

export const isSupabaseEnabled = Boolean(cloudEnabled && supabaseUrl && supabaseAnonKey)

export const supabase = isSupabaseEnabled
    ? createClient(supabaseUrl, supabaseAnonKey)
    : null
