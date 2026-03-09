-- Add onboarding conversation data fields to users_profiles table
ALTER TABLE public.users_profiles
ADD COLUMN IF NOT EXISTS goal_type TEXT CHECK (goal_type IN ('weight_loss', 'muscle_gain', 'maintenance', 'improved_energy', 'better_sleep')),
ADD COLUMN IF NOT EXISTS goal_by_when TEXT,
ADD COLUMN IF NOT EXISTS activity_level TEXT CHECK (activity_level IN ('sedentary', 'lightly_active', 'moderately_active', 'very_active', 'extremely_active')),
ADD COLUMN IF NOT EXISTS dietary_preferences TEXT[],
ADD COLUMN IF NOT EXISTS onboarding_completed BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMPTZ;

-- Create index for efficient queries on goal_type
CREATE INDEX IF NOT EXISTS idx_users_profiles_goal_type ON public.users_profiles(goal_type);

-- Create index for efficient queries on onboarding_completed
CREATE INDEX IF NOT EXISTS idx_users_profiles_onboarding_completed ON public.users_profiles(onboarding_completed);
