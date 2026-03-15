-- Create trigger function to auto-insert a free subscription row when a new user is created
CREATE OR REPLACE FUNCTION public.handle_new_user_subscription()
RETURNS TRIGGER AS $$
BEGIN
    BEGIN
        INSERT INTO public.subscriptions (user_id, subscription_status, is_active, credits)
        VALUES (NEW.id, 'free', true, '{}'::jsonb);
    EXCEPTION WHEN OTHERS THEN
        RAISE WARNING 'handle_new_user_subscription: failed to insert subscription for user %: %', NEW.id, SQLERRM;
    END;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Drop trigger if it already exists, then recreate
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;

-- Create trigger on auth.users to fire after each new user insert
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_new_user_subscription();
