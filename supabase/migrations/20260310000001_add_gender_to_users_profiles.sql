-- Add gender column to users_profiles table
-- This field is used by the AI agent for BMR/TDEE calculations

ALTER TABLE public.users_profiles
ADD COLUMN IF NOT EXISTS gender TEXT CHECK (gender IN ('male', 'female', 'other', 'prefer_not_to_say'));

-- Create index for efficient queries on gender
CREATE INDEX IF NOT EXISTS idx_users_profiles_gender ON public.users_profiles(gender);

-- Add comment to explain the column
COMMENT ON COLUMN public.users_profiles.gender IS 'User gender for health calculations (BMR/TDEE). Used by AI agent during onboarding.';
