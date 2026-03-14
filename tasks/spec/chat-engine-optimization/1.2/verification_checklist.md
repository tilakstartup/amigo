# Verification Checklist - Task 1.2: Write unit tests for SessionConfig restructuring

## Test Coverage Verification

### Unit Tests Execution
- [ ] All unit tests in `SessionConfigTest.kt` pass successfully
- [ ] Test suite runs without compilation errors on both platforms
- [ ] No test failures or errors reported

### Serialization Tests
- [ ] `hat` field is included in serialized JSON
- [ ] `initial_message` field is excluded from serialized JSON (transient)
- [ ] `data_to_be_collected` serializes correctly
- [ ] `data_to_be_calculated` serializes correctly
- [ ] Deserialization reconstructs SessionConfig correctly

### Field Validation Tests
- [ ] `hat` field cannot be empty string
- [ ] `hat` field cannot be blank
- [ ] Valid `hat` values are accepted
- [ ] `initial_message` can be null
- [ ] `initial_message` can be non-null string

### Registry Tests
- [ ] SessionConfigRegistry returns correct config for valid hat
- [ ] SessionConfigRegistry throws exception for unknown hat
- [ ] All registered session configs are accessible

### Platform-Specific Behavior
- [ ] Tests pass on Android (JVM target)
- [ ] Tests pass on iOS (Native target)
- [ ] Kotlin serialization works consistently across platforms

### Integration Points
- [ ] SessionConfig can be created with new structure
- [ ] SessionConfig can be serialized to JSON string
- [ ] SessionConfig can be deserialized from JSON string
- [ ] Registry lookup works for all session types

### Build Verification
- [ ] Shared module builds successfully: `./gradlew :shared:build`
- [ ] Android module builds successfully: `./gradlew :android:assembleDebug`
- [ ] iOS project builds successfully with xcodebuild

## Edge Cases
- [ ] Empty `hat` string is rejected
- [ ] Whitespace-only `hat` string is rejected
- [ ] Null `initial_message` is handled correctly
- [ ] Unknown session type throws appropriate exception
