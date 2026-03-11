-- Create users_profiles table
CREATE TABLE IF NOT EXISTS public.users_profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT NOT NULL UNIQUE,
    display_name TEXT,
    avatar_url TEXT,
    age INTEGER CHECK (age >= 13 AND age <= 120),
    height_cm NUMERIC(5,2),
    weight_kg NUMERIC(5,2),
    unit_preference TEXT CHECK (unit_preference IN ('metric', 'imperial')) DEFAULT 'metric',
    language TEXT DEFAULT 'en',
    theme TEXT CHECK (theme IN ('light', 'dark', 'auto')) DEFAULT 'auto',
    goal_type TEXT CHECK (goal_type IN ('weight_loss', 'muscle_gain', 'maintenance', 'improved_energy', 'better_sleep')),
    goal_by_when DATE,
    onboarding_completed BOOLEAN DEFAULT FALSE,
    onboarding_completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create index for efficient queries
CREATE INDEX idx_users_profiles_email ON public.users_profiles(email);

-- Enable Row Level Security
ALTER TABLE public.users_profiles ENABLE ROW LEVEL SECURITY;

-- RLS Policy: Users can only view their own profile
CREATE POLICY "Users can view own profile"
    ON public.users_profiles
    FOR SELECT
    USING (auth.uid() = id);

-- RLS Policy: Users can insert their own profile
CREATE POLICY "Users can insert own profile"
    ON public.users_profiles
    FOR INSERT
    WITH CHECK (auth.uid() = id);

-- RLS Policy: Users can update their own profile
CREATE POLICY "Users can update own profile"
    ON public.users_profiles
    FOR UPDATE
    USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id);

-- RLS Policy: Users can delete their own profile
CREATE POLICY "Users can delete own profile"
    ON public.users_profiles
    FOR DELETE
    USING (auth.uid() = id);

-- Create function to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION public.handle_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to update updated_at on users_profiles
CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON public.users_profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_updated_at();
