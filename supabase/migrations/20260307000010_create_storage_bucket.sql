-- Create storage bucket for food photos
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'food-photos',
    'food-photos',
    false,  -- Not public, requires authentication
    10485760,  -- 10MB file size limit
    ARRAY['image/jpeg', 'image/jpg', 'image/png', 'image/webp', 'image/heic']
)
ON CONFLICT (id) DO NOTHING;

-- RLS Policy: Users can view their own food photos
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE schemaname = 'storage' 
        AND tablename = 'objects' 
        AND policyname = 'Users can view own food photos'
    ) THEN
        CREATE POLICY "Users can view own food photos"
            ON storage.objects
            FOR SELECT
            USING (
                bucket_id = 'food-photos' AND
                auth.uid()::text = (storage.foldername(name))[1]
            );
    END IF;
END $$;

-- RLS Policy: Users can upload their own food photos
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE schemaname = 'storage' 
        AND tablename = 'objects' 
        AND policyname = 'Users can upload own food photos'
    ) THEN
        CREATE POLICY "Users can upload own food photos"
            ON storage.objects
            FOR INSERT
            WITH CHECK (
                bucket_id = 'food-photos' AND
                auth.uid()::text = (storage.foldername(name))[1]
            );
    END IF;
END $$;

-- RLS Policy: Users can update their own food photos
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE schemaname = 'storage' 
        AND tablename = 'objects' 
        AND policyname = 'Users can update own food photos'
    ) THEN
        CREATE POLICY "Users can update own food photos"
            ON storage.objects
            FOR UPDATE
            USING (
                bucket_id = 'food-photos' AND
                auth.uid()::text = (storage.foldername(name))[1]
            )
            WITH CHECK (
                bucket_id = 'food-photos' AND
                auth.uid()::text = (storage.foldername(name))[1]
            );
    END IF;
END $$;

-- RLS Policy: Users can delete their own food photos
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE schemaname = 'storage' 
        AND tablename = 'objects' 
        AND policyname = 'Users can delete own food photos'
    ) THEN
        CREATE POLICY "Users can delete own food photos"
            ON storage.objects
            FOR DELETE
            USING (
                bucket_id = 'food-photos' AND
                auth.uid()::text = (storage.foldername(name))[1]
            );
    END IF;
END $$;
