-- Add first_name and last_name columns to users_profiles table
-- These fields are used by the AI agent during onboarding
-- display_name will be auto-generated from first_name + last_name

ALTER TABLE public.users_profiles
ADD COLUMN IF NOT EXISTS first_name TEXT,
ADD COLUMN IF NOT EXISTS last_name TEXT;

-- Create function to auto-generate display_name from first_name and last_name
CREATE OR REPLACE FUNCTION public.generate_display_name()
RETURNS TRIGGER AS $$
BEGIN
    -- If first_name or last_name changed, update display_name
    IF (NEW.first_name IS NOT NULL OR NEW.last_name IS NOT NULL) THEN
        NEW.display_name = TRIM(CONCAT(COALESCE(NEW.first_name, ''), ' ', COALESCE(NEW.last_name, '')));
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to auto-generate display_name
CREATE TRIGGER generate_display_name_trigger
    BEFORE INSERT OR UPDATE OF first_name, last_name ON public.users_profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.generate_display_name();

-- Add comments
COMMENT ON COLUMN public.users_profiles.first_name IS 'User first name collected during onboarding';
COMMENT ON COLUMN public.users_profiles.last_name IS 'User last name collected during onboarding';
COMMENT ON COLUMN public.users_profiles.display_name IS 'Auto-generated from first_name + last_name, or manually set';
