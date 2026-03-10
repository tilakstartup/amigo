#!/usr/bin/env ruby
require 'xcodeproj'

project_path = 'Amigo.xcodeproj'
project = Xcodeproj::Project.open(project_path)

# Get the main target
target = project.targets.first

# Get the main group (Amigo folder)
amigo_group = project.main_group['Amigo']

# Add MainTabView.swift
main_tab_view = amigo_group.new_file('MainTabView.swift')
target.add_file_references([main_tab_view])

# Create Profile group if it doesn't exist
profile_group = amigo_group['Profile'] || amigo_group.new_group('Profile', 'Profile')

# Add Profile files
profile_view = profile_group.new_file('ProfileView.swift')
goal_management = profile_group.new_file('GoalManagementView.swift')
smart_goal = profile_group.new_file('SmartGoalPlanningView.swift')

target.add_file_references([profile_view, goal_management, smart_goal])

project.save
puts "Files added successfully"
