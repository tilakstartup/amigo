#!/usr/bin/env python3
"""
Start chat with goal_setting context
"""
from chat_agent import chat_with_agent

goal_setting_context = {
    "cap": "goal_setting",
    "responsibilities": [
        "Collect user data (weight, height, age, gender, activity_level, target_weight, target_date)",
        "Calculate metrics (BMR, TDEE, daily_calories, weekly_rate_kg)",
        "Validate daily calories meet USDA minimums (1200 F, 1500 M)",
        "Summarize complete goal plan",
        "Get user confirmation",
        "Save goal only after confirmation"
    ],
    "collect_data": ["weight", "height", "age", "gender", "activity_level", "target_weight", "target_date"],
    "collect_metrics": ["bmr", "tdee", "daily_calories", "weekly_rate_kg"]
}

if __name__ == '__main__':
    chat_with_agent('4XLAIQ6BUY', session_context=goal_setting_context)
