#!/usr/bin/env python3
import re

# Read the project file
with open('/Users/tilakputta/projects/apps/healthamigoai/mobile/ios/Amigo.xcodeproj/project.pbxproj', 'r') as f:
    content = f.read()

# Remove references to ConversationalOnboardingView and ConversationalOnboardingViewModel
content = re.sub(r'\t\t[A-F0-9]{24} /\* ConversationalOnboardingView\.swift in Sources \*/ = \{isa = PBXBuildFile; fileRef = [A-F0-9]{24} /\* ConversationalOnboardingView\.swift \*/; \};\n', '', content)
content = re.sub(r'\t\t[A-F0-9]{24} /\* ConversationalOnboardingViewModel\.swift in Sources \*/ = \{isa = PBXBuildFile; fileRef = [A-F0-9]{24} /\* ConversationalOnboardingViewModel\.swift \*/; \};\n', '', content)
content = re.sub(r'\t\t[A-F0-9]{24} /\* ConversationalOnboardingView\.swift \*/ = \{isa = PBXFileReference; lastKnownFileType = sourcecode\.swift; path = ConversationalOnboardingView\.swift; sourceTree = "<group>"; \};\n', '', content)
content = re.sub(r'\t\t[A-F0-9]{24} /\* ConversationalOnboardingViewModel\.swift \*/ = \{isa = PBXFileReference; lastKnownFileType = sourcecode\.swift; path = ConversationalOnboardingViewModel.swift; sourceTree = "<group>"; \};\n', '', content)
content = re.sub(r'\t\t\t\t[A-F0-9]{24} /\* ConversationalOnboardingView\.swift in Sources \*/,\n', '', content)
content = re.sub(r'\t\t\t\t[A-F0-9]{24} /\* ConversationalOnboardingViewModel\.swift in Sources \*/,\n', '', content)
content = re.sub(r'\t\t\t\t[A-F0-9]{24} /\* ConversationalOnboardingView\.swift \*/,\n', '', content)
content = re.sub(r'\t\t\t\t[A-F0-9]{24} /\* ConversationalOnboardingViewModel\.swift \*/,\n', '', content)

# Write back
with open('/Users/tilakputta/projects/apps/healthamigoai/mobile/ios/Amigo.xcodeproj/project.pbxproj', 'w') as f:
    f.write(content)

print("Project file updated successfully")
