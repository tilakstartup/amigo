-- Create custom_access_token_hook to inject subscription claims into JWTs at mint time
CREATE OR REPLACE FUNCTION public.custom_access_token_hook(event jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    sub_record RECORD;
    claims     jsonb;
BEGIN
    claims := event->'claims';

    BEGIN
        SELECT subscription_status, is_active, credits
          INTO sub_record
          FROM public.subscriptions
         WHERE user_id = (event->>'user_id')::uuid;

        IF FOUND THEN
            claims := jsonb_set(claims, '{user_subscription}', jsonb_build_object(
                'subscription_status', sub_record.subscription_status,
                'is_active',           sub_record.is_active,
                'credits',             COALESCE(sub_record.credits, '{}'::jsonb)
            ));
        ELSE
            claims := jsonb_set(claims, '{user_subscription}',
                '{"subscription_status":"free","is_active":true,"credits":{}}'::jsonb
            );
        END IF;
    EXCEPTION WHEN OTHERS THEN
        claims := jsonb_set(claims, '{user_subscription}',
            '{"subscription_status":"free","is_active":true,"credits":{}}'::jsonb
        );
    END;

    RETURN jsonb_set(event, '{claims}', claims);
END;
$$;

-- Restrict execution to supabase_auth_admin only (security best practice)
REVOKE EXECUTE ON FUNCTION public.custom_access_token_hook FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.custom_access_token_hook TO supabase_auth_admin;
