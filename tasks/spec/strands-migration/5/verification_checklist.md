# Task 5 Verification Checklist — `accumulate.ts`

## Implementation Checks

- [x] `supabase/functions/_shared/accumulate.ts` exists ✅
- [x] `accumulateDataCollected` exported with correct signature ✅
- [x] Rule 1: non-null value overwrites/adds field ✅ auto-verified
- [x] Rule 2: null value + field absent → stores null ✅ auto-verified
- [x] Rule 3: null value + field present → no-op (preserves existing) ✅ auto-verified
- [x] Empty string `""` treated as null ✅
- [x] String `"null"` treated as null ✅
- [x] Returns new object (does not mutate existing) ✅
- [x] Returns existing unchanged when `prev` is null ✅

## Type Correctness

- [x] `deno check` exits 0 with no errors ✅ auto-verified

## Property Tests (Task 5.1)

- [x] Property 4: double accumulation of same non-null value is idempotent (200 runs) ✅
- [x] Property 5: null value never overwrites existing non-null value (200 runs) ✅
- [x] Property 6: all pre-existing fields preserved when new field added (200 runs) ✅
