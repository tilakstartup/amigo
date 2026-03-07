# Requirements Document

## Introduction

Amigo is an AI-first personal health coaching application centered around "Amigo", an AI health mentor that acts as the user's friend and personal coach. The system enables users to log meals through multiple input methods (image, voice, text), receive personalized health guidance, and access features based on their subscription tier (Free or Pro).

## Glossary

- **Amigo**: The complete AI-powered health coaching application system
- **Amigo**: The AI health mentor that provides personalized coaching and health guidance (also the name of the application itself)
- **User**: A person who uses Amigo to track their health and receive coaching
- **User_Account**: A registered account containing user credentials and profile information
- **Authentication_System**: Component that verifies user identity and manages sessions
- **Email_Authenticator**: Component that handles email and password authentication
- **Google_Authenticator**: Component that handles Google OAuth authentication
- **Apple_Authenticator**: Component that handles Sign in with Apple authentication
- **Session_Token**: A secure credential that maintains user authentication state
- **Password_Hash**: A cryptographically hashed representation of a user's password
- **OAuth_Provider**: External authentication service (Google or Apple)
- **Meal_Log**: A recorded meal entry with nutritional information and timestamp
- **Image_Logger**: Component that processes food photos to create meal logs
- **Voice_Logger**: Component that processes voice input to create meal logs
- **Text_Logger**: Component that processes text input to create meal logs
- **Subscription_Tier**: The user's subscription level (Free or Pro)
- **Usage_Quota**: The remaining number of meal logs available for a user's subscription tier
- **AI_Coach**: The AI system that provides personalized health coaching through Amigo
- **Chat_Interface**: The conversational interface for interacting with Amigo
- **Data_Store**: The persistent storage system for meal logs, user data, and subscription information
- **Health_Platform**: External health tracking services (Fitbit, Garmin, Apple Health, Google Health Connect)
- **Platform_Connector**: Component that authenticates and communicates with Health_Platform APIs
- **Health_Metric**: Quantified health data such as steps, heart rate, sleep duration, exercise sessions
- **Sync_Operation**: The process of retrieving Health_Metrics from a Health_Platform and storing them locally
- **OAuth_Token**: Authentication credential for accessing a Health_Platform on behalf of a user
- **Metric_Store**: Storage system for Health_Metrics retrieved from Health_Platforms
- **USDA_API**: The USDA FoodData Central API that provides comprehensive nutritional information
- **Food_Database**: The nutritional information database accessed through USDA_API
- **Barcode_Scanner**: Component that captures and decodes product barcodes
- **Barcode_API**: External API service that identifies packaged foods from barcode data
- **Product_Identifier**: The unique barcode number (UPC/EAN) that identifies a packaged food product
- **Nutritional_Data**: Detailed nutritional information including calories, macronutrients, micronutrients, and serving sizes
- **Food_Item**: A specific food or ingredient with associated Nutritional_Data
- **Packaged_Food**: A commercial food product with a Product_Identifier and standardized Nutritional_Data
- **API_Key**: Authentication credential for accessing USDA_API or Barcode_API
- **Search_Query**: User input text used to find Food_Items in the Food_Database
- **Nutrition_Enricher**: Component that enhances meal logs with verified Nutritional_Data from external sources
- **Water_Log**: A recorded entry of water consumption with volume and timestamp
- **Water_Tracker**: Component that manages water intake logging and goal tracking
- **Water_Goal**: The target daily water intake volume set by the user
- **Hydration_Progress**: The current water intake relative to the Water_Goal for a given day
- **Water_Reminder**: A notification prompting the user to drink water
- **Hydration_History**: Historical record of daily water intake over time
- **Fasting_Session**: A recorded period during which the user abstains from caloric intake
- **Fasting_Tracker**: Component that manages fasting session timing and history
- **Fasting_Timer**: Component that tracks elapsed time during an active Fasting_Session
- **Fasting_Protocol**: A predefined fasting schedule pattern such as 16:8, 18:6, or 20:4
- **Fasting_Duration**: The length of time for a completed or active Fasting_Session
- **Fasting_Streak**: The number of consecutive days the user has completed a fasting goal
- **Fasting_History**: Historical record of completed Fasting_Sessions
- **Fasting_Notification**: An alert informing the user when a Fasting_Session period ends
- **Onboarding_Flow**: The guided process for new users to set up their profile and learn app features
- **Profile_Setup**: The initial configuration of user information including name, age, height, weight, and health goals
- **Tutorial**: An interactive walkthrough that demonstrates key app features to new users
- **Permission_Request**: A system prompt asking users to grant access to device features like camera or notifications
- **Payment_Gateway**: External payment processing service that handles subscription transactions
- **Payment_Method**: User's stored payment information such as credit card or digital wallet
- **Subscription_Purchase**: The transaction process for acquiring a Pro subscription
- **Trial_Period**: A limited time period during which users can access Pro features before payment
- **Receipt**: A transaction record provided after a successful subscription purchase
- **Purchase_Restoration**: The process of recovering previously purchased subscriptions on a new device
- **Push_Notification**: A message sent to the user's device even when the app is not actively open
- **Notification_Preferences**: User settings that control which notifications are enabled and when they are sent
- **Notification_Scheduler**: Component that manages the timing and delivery of Push_Notifications
- **Deep_Link**: A notification link that opens the app to a specific feature or screen
- **Dashboard**: The main home screen displaying daily health summary and quick access to features
- **Daily_Summary**: An overview of the user's health metrics and progress for the current day
- **Activity_Feed**: A chronological list of recent user actions and health events
- **Quick_Stats**: Key health metrics displayed prominently on the Dashboard
- **Data_Export**: The process of generating user data in a downloadable format
- **Export_Format**: The file format for exported data such as JSON or CSV
- **GDPR_Compliance**: Adherence to European data protection regulations including data portability rights
- **Account_Deletion**: The process of permanently removing a user account and all associated data
- **Privacy_Policy**: The legal document describing how user data is collected, used, and protected
- **Terms_of_Service**: The legal agreement between the user and Amigo
- **Data_Retention_Policy**: Rules governing how long user data is stored before deletion
- **Unit_Preferences**: User settings for measurement systems such as metric or imperial
- **Language_Settings**: User's preferred language for app interface and content
- **Theme_Settings**: User's preferred visual appearance such as light mode or dark mode
- **Crash_Reporter**: Component that captures and logs application crashes for debugging
- **Error_Logger**: Component that records error events and diagnostic information
- **Error_Message**: User-friendly text explaining what went wrong and how to resolve it
- **Network_Handler**: Component that manages connectivity status and offline behavior
- **Graceful_Degradation**: The ability to provide reduced functionality when services are unavailable
- **Supabase**: The backend-as-a-service platform providing authentication, database, storage, and real-time features
- **Supabase_Auth**: The authentication service component of Supabase
- **Supabase_Database**: The PostgreSQL database component of Supabase
- **Supabase_Storage**: The file storage component of Supabase for storing images and media
- **Supabase_Realtime**: The real-time subscription component of Supabase for live data updates
- **Row_Level_Security**: Database security policies that restrict data access based on user identity
- **RLS_Policy**: A specific security rule that controls which rows a user can access in a database table
- **Database_Table**: A structured data storage entity in Supabase_Database
- **Storage_Bucket**: A container for organizing files in Supabase_Storage
- **Realtime_Channel**: A communication channel for receiving live data updates from Supabase_Realtime
- **Amazon_Bedrock**: The AWS service providing access to foundation models including Claude
- **Claude_AI**: The Anthropic AI model accessed through Amazon_Bedrock for natural language processing and analysis
- **Bedrock_Client**: The component that communicates with Amazon_Bedrock API
- **AI_Image_Analyzer**: Component using Claude_AI to identify food items from photos
- **AI_Text_Processor**: Component using Claude_AI to parse text-based meal descriptions
- **AI_Speech_Transcriber**: Component using Claude_AI to convert voice input to text
- **AI_Conversation_Engine**: Component using Claude_AI to power Amigo coaching conversations
- **AI_Insight_Generator**: Component using Claude_AI to generate personalized health insights
- **User_AI_Context**: Personalized AI context data that evolves with each user's interactions
- **Conversation_History**: Record of past interactions between a user and Amigo
- **User_Pattern_Profile**: Analyzed patterns of user behavior including meal timing, food preferences, and fasting habits
- **AI_Memory_Store**: Storage system for User_AI_Context and Conversation_History
- **Context_Window**: The amount of historical data provided to Claude_AI for generating personalized responses
- **Pattern_Analyzer**: Component that identifies trends and patterns in user health data
- **Preference_Tracker**: Component that learns and stores user preferences over time
- **Coaching_Adaptation_Engine**: Component that adjusts Amigo's coaching style based on user progress and challenges
- **Session_Context**: The relevant user data and history loaded for a specific AI interaction
- **Health_Goal**: A specific health objective set by the user such as weight loss, muscle gain, maintenance, improved energy, or better sleep
- **Active_Goal**: The current Health_Goal that the user is actively pursuing
- **Goal_History**: Historical record of previous Health_Goals with summaries and outcomes
- **Goal_Transition**: The event of switching from one Health_Goal to another
- **Goal_Context**: AI context data specific to a particular Health_Goal including patterns and progress
- **Goal_Summary**: A brief record of a completed or paused Health_Goal including duration and outcomes
- **Food_Photo**: An image of food stored in Supabase_Storage for meal logging
- **Subscription_Record**: Database entry tracking user subscription tier and payment status
- **Payment_Transaction**: Record of a subscription purchase or renewal stored in Supabase_Database

## Requirements

### Requirement 1: Account Creation with Email and Password

**User Story:** As a new user, I want to create an account with my email and password, so that I can access Amigo

#### Acceptance Criteria

1. WHEN a user provides an email address and password, THE Email_Authenticator SHALL validate the email format
2. WHEN the email format is valid, THE Email_Authenticator SHALL verify the email address is not already registered
3. WHEN the email is available, THE Email_Authenticator SHALL create a User_Account with the provided email
4. WHEN creating a User_Account, THE Email_Authenticator SHALL hash the password using a cryptographic algorithm and store the Password_Hash
5. WHEN account creation succeeds, THE Authentication_System SHALL generate a Session_Token for the user
6. IF the email is already registered, THEN Amigo SHALL return an error message indicating the email is in use
7. IF the password does not meet security requirements, THEN Amigo SHALL return an error message with password requirements
8. THE Email_Authenticator SHALL require passwords to be at least 8 characters long

### Requirement 2: Sign In with Email and Password

**User Story:** As a registered user, I want to sign in with my email and password, so that I can access my health data

#### Acceptance Criteria

1. WHEN a user provides an email and password, THE Email_Authenticator SHALL retrieve the User_Account associated with the email
2. WHEN the User_Account exists, THE Email_Authenticator SHALL verify the provided password against the stored Password_Hash
3. WHEN the password is correct, THE Authentication_System SHALL generate a Session_Token for the user
4. WHEN authentication succeeds, Amigo SHALL grant access to the user's data and features
5. IF the email does not exist, THEN Amigo SHALL return an authentication error message
6. IF the password is incorrect, THEN Amigo SHALL return an authentication error message
7. THE Authentication_System SHALL limit failed login attempts to prevent brute force attacks

### Requirement 3: Account Creation with Google

**User Story:** As a new user, I want to create an account using my Google account, so that I can quickly sign up without creating a new password

#### Acceptance Criteria

1. WHEN a user initiates Google sign-in, THE Google_Authenticator SHALL redirect to Google's OAuth authorization flow
2. WHEN Google OAuth authorization succeeds, THE Google_Authenticator SHALL receive an OAuth_Token and user profile information
3. WHEN user profile information is received, THE Google_Authenticator SHALL verify the Google account email is not already registered
4. WHEN the email is available, THE Google_Authenticator SHALL create a User_Account linked to the Google account
5. WHEN account creation succeeds, THE Authentication_System SHALL generate a Session_Token for the user
6. IF the Google account email is already registered, THEN Amigo SHALL return an error message indicating the account exists
7. IF Google OAuth authorization fails, THEN Amigo SHALL return a descriptive error message

### Requirement 4: Sign In with Google

**User Story:** As a registered user, I want to sign in using my Google account, so that I can access my health data without entering a password

#### Acceptance Criteria

1. WHEN a user initiates Google sign-in, THE Google_Authenticator SHALL redirect to Google's OAuth authorization flow
2. WHEN Google OAuth authorization succeeds, THE Google_Authenticator SHALL receive an OAuth_Token and user profile information
3. WHEN user profile information is received, THE Google_Authenticator SHALL retrieve the User_Account linked to the Google account
4. WHEN the User_Account exists, THE Authentication_System SHALL generate a Session_Token for the user
5. WHEN authentication succeeds, Amigo SHALL grant access to the user's data and features
6. IF no User_Account is linked to the Google account, THEN Amigo SHALL return an error message
7. IF Google OAuth authorization fails, THEN Amigo SHALL return a descriptive error message

### Requirement 5: Account Creation with Apple

**User Story:** As a new iOS user, I want to create an account using Sign in with Apple, so that I can quickly sign up with my Apple ID

#### Acceptance Criteria

1. WHERE iOS platform is active, WHEN a user initiates Apple sign-in, THE Apple_Authenticator SHALL redirect to Apple's OAuth authorization flow
2. WHEN Apple OAuth authorization succeeds, THE Apple_Authenticator SHALL receive an OAuth_Token and user profile information
3. WHEN user profile information is received, THE Apple_Authenticator SHALL verify the Apple account identifier is not already registered
4. WHEN the identifier is available, THE Apple_Authenticator SHALL create a User_Account linked to the Apple account
5. WHEN account creation succeeds, THE Authentication_System SHALL generate a Session_Token for the user
6. IF the Apple account is already registered, THEN Amigo SHALL return an error message indicating the account exists
7. IF Apple OAuth authorization fails, THEN Amigo SHALL return a descriptive error message
8. THE Apple_Authenticator SHALL handle cases where the user chooses to hide their email address

### Requirement 6: Sign In with Apple

**User Story:** As a registered iOS user, I want to sign in using Sign in with Apple, so that I can access my health data securely

#### Acceptance Criteria

1. WHERE iOS platform is active, WHEN a user initiates Apple sign-in, THE Apple_Authenticator SHALL redirect to Apple's OAuth authorization flow
2. WHEN Apple OAuth authorization succeeds, THE Apple_Authenticator SHALL receive an OAuth_Token and user profile information
3. WHEN user profile information is received, THE Apple_Authenticator SHALL retrieve the User_Account linked to the Apple account
4. WHEN the User_Account exists, THE Authentication_System SHALL generate a Session_Token for the user
5. WHEN authentication succeeds, Amigo SHALL grant access to the user's data and features
6. IF no User_Account is linked to the Apple account, THEN Amigo SHALL return an error message
7. IF Apple OAuth authorization fails, THEN Amigo SHALL return a descriptive error message

### Requirement 7: Session Management

**User Story:** As a user, I want my login session to persist, so that I don't have to sign in every time I open the app

#### Acceptance Criteria

1. WHEN a user successfully authenticates, THE Authentication_System SHALL store the Session_Token securely on the device
2. WHEN Amigo launches, THE Authentication_System SHALL validate the stored Session_Token
3. WHEN the Session_Token is valid, Amigo SHALL grant access without requiring re-authentication
4. WHEN the Session_Token expires, Amigo SHALL require the user to sign in again
5. THE Authentication_System SHALL allow users to sign out, which invalidates the Session_Token
6. WHEN a user signs out, THE Authentication_System SHALL delete the Session_Token from the device

### Requirement 8: Account Security and Data Protection

**User Story:** As a user, I want my account credentials stored securely, so that my personal information is protected

#### Acceptance Criteria

1. THE Authentication_System SHALL encrypt all Session_Tokens at rest
2. THE Authentication_System SHALL transmit all authentication requests over secure connections
3. THE Email_Authenticator SHALL never store passwords in plain text
4. THE Authentication_System SHALL use industry-standard cryptographic algorithms for Password_Hash generation
5. THE Authentication_System SHALL securely store OAuth_Tokens received from OAuth_Providers
6. WHEN a user deletes their account, THE Authentication_System SHALL delete all stored credentials and Session_Tokens

### Requirement 9: AI Health Mentor - Amigo

**User Story:** As a user, I want an AI health mentor named Amigo, so that I can receive personalized health coaching and guidance

#### Acceptance Criteria

1. Amigo SHALL provide an AI health mentor named Amigo
2. THE Amigo SHALL communicate with users in a friendly, supportive coaching tone
3. THE Amigo SHALL provide personalized health guidance based on user meal logs and health data
4. WHEN a user interacts with Amigo, THE Chat_Interface SHALL display the conversation history
5. THE Amigo SHALL reference the user's meal history when providing coaching advice

### Requirement 10: Image-Based Meal Logging

**User Story:** As a user, I want to log meals by taking photos of my food, so that I can quickly track what I eat

#### Acceptance Criteria

1. WHEN a user captures a food photo, THE Image_Logger SHALL process the image to identify food items
2. WHEN the Image_Logger identifies food items, Amigo SHALL create a Meal_Log with estimated nutritional information
3. Amigo SHALL store the food photo with the Meal_Log
4. WHEN image processing fails, Amigo SHALL return a descriptive error message
5. THE Image_Logger SHALL extract calorie estimates, macronutrients, and portion sizes from the image

### Requirement 11: Voice-Based Meal Logging

**User Story:** As a user, I want to log meals by speaking what I ate, so that I can track meals hands-free

#### Acceptance Criteria

1. WHEN a user provides voice input describing a meal, THE Voice_Logger SHALL transcribe the audio to text
2. WHEN the Voice_Logger transcribes the input, Amigo SHALL parse the food items and quantities
3. Amigo SHALL create a Meal_Log with nutritional information based on the parsed voice input
4. WHEN voice transcription fails, Amigo SHALL return a descriptive error message
5. THE Voice_Logger SHALL support natural language descriptions of meals

### Requirement 12: Text-Based Meal Logging

**User Story:** As a user, I want to log meals by typing what I ate, so that I can quickly enter meal information

#### Acceptance Criteria

1. WHEN a user submits text describing a meal, THE Text_Logger SHALL parse the food items and quantities
2. Amigo SHALL create a Meal_Log with nutritional information based on the parsed text
3. THE Text_Logger SHALL support natural language meal descriptions
4. WHEN text parsing fails, Amigo SHALL return a descriptive error message
5. THE Text_Logger SHALL allow users to specify portion sizes and quantities

### Requirement 13: Free Tier Subscription

**User Story:** As a free user, I want access to basic meal logging features, so that I can try the app before upgrading

#### Acceptance Criteria

1. WHERE Free tier is active, Amigo SHALL allow 3 image-based meal logs per week
2. WHERE Free tier is active, Amigo SHALL allow 5 voice-based meal logs per week
3. WHERE Free tier is active, Amigo SHALL allow 10 text-based meal logs per week
4. WHEN a Free tier user exceeds their Usage_Quota, Amigo SHALL display an upgrade prompt
5. Amigo SHALL track and display remaining Usage_Quota for Free tier users
6. WHERE Free tier is active, Amigo SHALL provide basic meal logging features without AI coaching

### Requirement 14: Pro Tier Subscription

**User Story:** As a Pro subscriber, I want unlimited meal logging and full AI coaching with Amigo, so that I can get comprehensive health guidance

#### Acceptance Criteria

1. WHERE Pro tier is active, Amigo SHALL provide unlimited meal logging for all input methods
2. WHERE Pro tier is active, Amigo SHALL enable full AI analysis capabilities for meal identification and nutritional analysis
3. WHERE Pro tier is active, Amigo SHALL provide personal coaching chat with Amigo
4. WHERE Pro tier is active, Amigo SHALL provide advanced health insights and personalized analysis
5. WHERE Pro tier is active, THE Amigo SHALL offer proactive coaching suggestions based on meal patterns
6. WHERE Pro tier is active, Amigo SHALL provide detailed micronutrient information and extended history
7. WHERE Pro tier is active, Amigo SHALL enable AI-generated health insights and trend analysis
8. WHERE Pro tier is active, Amigo SHALL provide enhanced water and fasting statistics
9. WHERE Pro tier is active, Amigo SHALL provide monthly fasting trend analysis
10. WHERE Pro tier is active, Amigo SHALL correlate fasting patterns with weight trends when weight data is available

### Requirement 15: Subscription Management

**User Story:** As a user, I want to manage my subscription, so that I can upgrade or downgrade my plan

#### Acceptance Criteria

1. Amigo SHALL allow users to view their current Subscription_Tier
2. Amigo SHALL allow users to upgrade from Free to Pro
3. Amigo SHALL allow users to downgrade from Pro to Free
4. WHEN a user upgrades their subscription, Amigo SHALL immediately apply the new tier benefits
5. WHEN a user downgrades their subscription, Amigo SHALL maintain Pro access until the end of the current billing period
6. Amigo SHALL display the features available at each Subscription_Tier

### Requirement 16: Usage Quota Tracking

**User Story:** As a user, I want to see how many meal logs I have remaining, so that I can manage my usage

#### Acceptance Criteria

1. Amigo SHALL track the number of meal logs used for each input method
2. WHEN a user creates a Meal_Log, Amigo SHALL decrement the appropriate Usage_Quota
3. Amigo SHALL display remaining Usage_Quota for each meal logging method
4. WHEN a Usage_Quota reaches zero, Amigo SHALL prevent additional logs of that type
5. WHEN a subscription period renews, Amigo SHALL reset Usage_Quota to the tier limits

### Requirement 17: Amigo Chat Interface (Pro Tier)

**User Story:** As a Pro subscriber, I want to chat with Amigo about my health, so that I can get personalized coaching

#### Acceptance Criteria

1. WHERE Pro tier is active, THE Chat_Interface SHALL allow users to send messages to Amigo
2. WHEN a Pro user sends a message, THE AI_Coach SHALL generate a personalized response
3. THE Chat_Interface SHALL display the conversation history with Amigo
4. THE AI_Coach SHALL reference the user's meal history when responding to questions
5. WHERE Free tier is active, Amigo SHALL display the chat feature as locked with an upgrade prompt

### Requirement 18: Meal Log History

**User Story:** As a user, I want to view my meal logging history, so that I can review what I've eaten

#### Acceptance Criteria

1. WHEN a user requests their meal history, Amigo SHALL display all Meal_Logs in chronological order
2. Amigo SHALL display the input method used for each Meal_Log
3. Amigo SHALL display nutritional information for each Meal_Log
4. Amigo SHALL allow users to filter meal history by date range
5. WHEN a Meal_Log includes a photo, Amigo SHALL display the photo in the history

### Requirement 19: Data Persistence

**User Story:** As a user, I want my meal logs and subscription data saved securely, so that I don't lose my tracking history

#### Acceptance Criteria

1. WHEN a user creates a Meal_Log, THE Data_Store SHALL persist the data immediately
2. WHEN Amigo restarts, THE Data_Store SHALL restore all meal logs and subscription information
3. THE Data_Store SHALL maintain data integrity across application sessions
4. IF a storage operation fails, THEN Amigo SHALL notify the user and retain the data in memory
5. THE Data_Store SHALL ensure each Meal_Log has a unique identifier

### Requirement 20: Nutritional Analysis

**User Story:** As a user, I want to see nutritional information for my meals, so that I can understand what I'm eating

#### Acceptance Criteria

1. WHEN a Meal_Log is created, Amigo SHALL calculate total calories
2. WHEN a Meal_Log is created, Amigo SHALL calculate macronutrients (protein, carbohydrates, fats)
3. Amigo SHALL display daily nutritional summaries
4. Amigo SHALL display weekly nutritional trends
5. WHERE Pro tier is active, Amigo SHALL provide detailed micronutrient information

### Requirement 21: Health Platform Authentication

**User Story:** As a user, I want to connect my Fitbit, Garmin, Apple Health, or Google Health Connect account, so that Amigo can access my health data for better coaching

#### Acceptance Criteria

1. Amigo SHALL support authentication with Fitbit, Garmin, Apple Health, and Google Health Connect
2. WHEN a user initiates platform connection, THE Platform_Connector SHALL redirect to the Health_Platform OAuth authorization flow
3. WHEN OAuth authorization succeeds, THE Platform_Connector SHALL store the OAuth_Token securely
4. WHEN OAuth authorization fails, Amigo SHALL display a descriptive error message
5. Amigo SHALL allow users to disconnect a Health_Platform at any time
6. WHEN a user disconnects a Health_Platform, Amigo SHALL revoke the OAuth_Token

### Requirement 22: Fitbit Integration

**User Story:** As a user, I want to sync my Fitbit data, so that Amigo can use my activity and health metrics for coaching

#### Acceptance Criteria

1. WHEN a Fitbit OAuth_Token is valid, THE Platform_Connector SHALL retrieve steps, heart rate, sleep, and exercise data from Fitbit
2. WHEN Fitbit data is retrieved, Amigo SHALL store the Health_Metrics in the Metric_Store
3. THE Platform_Connector SHALL sync Fitbit data at least once per day
4. IF Fitbit API access fails, THEN Amigo SHALL log the error and retry the Sync_Operation
5. THE Platform_Connector SHALL respect Fitbit API rate limits

### Requirement 23: Garmin Integration

**User Story:** As a user, I want to sync my Garmin data, so that Amigo can use my activity and health metrics for coaching

#### Acceptance Criteria

1. WHEN a Garmin OAuth_Token is valid, THE Platform_Connector SHALL retrieve steps, heart rate, sleep, and exercise data from Garmin
2. WHEN Garmin data is retrieved, Amigo SHALL store the Health_Metrics in the Metric_Store
3. THE Platform_Connector SHALL sync Garmin data at least once per day
4. IF Garmin API access fails, THEN Amigo SHALL log the error and retry the Sync_Operation
5. THE Platform_Connector SHALL respect Garmin API rate limits

### Requirement 24: Apple Health Integration

**User Story:** As a user, I want to sync my Apple Health data, so that Amigo can use my activity and health metrics for coaching

#### Acceptance Criteria

1. WHEN Apple Health authorization is granted, THE Platform_Connector SHALL retrieve steps, heart rate, sleep, and exercise data from Apple Health
2. WHEN Apple Health data is retrieved, Amigo SHALL store the Health_Metrics in the Metric_Store
3. THE Platform_Connector SHALL sync Apple Health data at least once per day
4. IF Apple Health access fails, THEN Amigo SHALL log the error and retry the Sync_Operation
5. WHERE iOS platform is active, THE Platform_Connector SHALL use HealthKit framework for Apple Health access

### Requirement 25: Google Health Connect Integration

**User Story:** As a user, I want to sync my Google Health Connect data, so that Amigo can use my activity and health metrics for coaching

#### Acceptance Criteria

1. WHEN Google Health Connect authorization is granted, THE Platform_Connector SHALL retrieve steps, heart rate, sleep, and exercise data from Google Health Connect
2. WHEN Google Health Connect data is retrieved, Amigo SHALL store the Health_Metrics in the Metric_Store
3. THE Platform_Connector SHALL sync Google Health Connect data at least once per day
4. IF Google Health Connect access fails, THEN Amigo SHALL log the error and retry the Sync_Operation
5. WHERE Android platform is active, THE Platform_Connector SHALL use Health Connect API for data access

### Requirement 26: Health Metrics Storage

**User Story:** As a user, I want my synced health metrics stored securely, so that Amigo can reference them over time

#### Acceptance Criteria

1. WHEN a Sync_Operation completes, THE Metric_Store SHALL persist all retrieved Health_Metrics
2. THE Metric_Store SHALL associate each Health_Metric with a timestamp and source Health_Platform
3. THE Metric_Store SHALL prevent duplicate Health_Metrics from the same source and timestamp
4. WHEN Amigo restarts, THE Metric_Store SHALL restore all Health_Metrics
5. THE Metric_Store SHALL maintain data integrity across application sessions

### Requirement 27: Health Metrics Display

**User Story:** As a user, I want to view my synced health metrics, so that I can see my activity and health data in one place

#### Acceptance Criteria

1. Amigo SHALL display daily step counts from connected Health_Platforms
2. Amigo SHALL display heart rate data from connected Health_Platforms
3. Amigo SHALL display sleep duration and quality from connected Health_Platforms
4. Amigo SHALL display exercise sessions from connected Health_Platforms
5. Amigo SHALL indicate the source Health_Platform for each Health_Metric

### Requirement 28: Amigo Health Metrics Integration

**User Story:** As a Pro subscriber, I want Amigo to reference my health metrics when coaching me, so that I receive comprehensive personalized guidance

#### Acceptance Criteria

1. WHERE Pro tier is active, THE AI_Coach SHALL access Health_Metrics when generating coaching responses
2. WHEN Amigo provides coaching, THE AI_Coach SHALL reference relevant Health_Metrics such as activity levels and sleep patterns
3. THE AI_Coach SHALL correlate meal logs with exercise data to provide contextual guidance
4. THE AI_Coach SHALL identify patterns across Health_Metrics and meal logs
5. WHEN Health_Metrics indicate concerning patterns, THE AI_Coach SHALL proactively suggest health improvements

### Requirement 29: Sync Status and Error Handling

**User Story:** As a user, I want to know when my health data was last synced, so that I can ensure my data is up to date

#### Acceptance Criteria

1. Amigo SHALL display the last successful sync timestamp for each connected Health_Platform
2. WHEN a Sync_Operation is in progress, Amigo SHALL display a sync status indicator
3. IF a Sync_Operation fails, THEN Amigo SHALL display an error message with troubleshooting guidance
4. Amigo SHALL allow users to manually trigger a Sync_Operation
5. WHEN a manual sync is triggered, THE Platform_Connector SHALL immediately attempt to sync data from all connected Health_Platforms

### Requirement 30: Health Platform Data Privacy

**User Story:** As a user, I want my health platform data handled securely, so that my sensitive health information is protected

#### Acceptance Criteria

1. Amigo SHALL encrypt OAuth_Tokens at rest
2. Amigo SHALL transmit all Health_Platform API requests over secure connections
3. Amigo SHALL only request the minimum necessary permissions from each Health_Platform
4. WHEN a user deletes their account, Amigo SHALL delete all stored Health_Metrics and OAuth_Tokens
5. Amigo SHALL comply with health data privacy regulations for Health_Metrics storage and processing

### Requirement 31: USDA FoodData Central API Integration

**User Story:** As a user, I want access to comprehensive USDA nutritional data, so that my meal logs contain accurate and verified nutritional information

#### Acceptance Criteria

1. Amigo SHALL integrate with USDA FoodData Central API to retrieve Nutritional_Data
2. WHEN Amigo initializes, Amigo SHALL authenticate with USDA_API using a valid API_Key
3. Amigo SHALL store the USDA API_Key securely
4. Amigo SHALL transmit all USDA_API requests over secure connections
5. IF USDA_API authentication fails, THEN Amigo SHALL log the error and use cached nutritional data
6. Amigo SHALL respect USDA_API rate limits and usage policies

### Requirement 32: Food Search via USDA Database

**User Story:** As a user, I want to search for foods in the USDA database, so that I can find accurate nutritional information for any food

#### Acceptance Criteria

1. WHEN a user submits a Search_Query, Amigo SHALL query the Food_Database through USDA_API
2. WHEN USDA_API returns search results, Amigo SHALL display matching Food_Items with their names and categories
3. WHEN a user selects a Food_Item, Amigo SHALL retrieve complete Nutritional_Data from USDA_API
4. Amigo SHALL display calories, macronutrients, micronutrients, and serving sizes for the selected Food_Item
5. IF USDA_API search fails, THEN Amigo SHALL return a descriptive error message
6. Amigo SHALL cache frequently accessed Nutritional_Data to reduce API calls

### Requirement 33: Barcode Scanning Capability

**User Story:** As a user, I want to scan product barcodes with my camera, so that I can quickly identify and log packaged foods

#### Acceptance Criteria

1. WHEN a user activates barcode scanning, THE Barcode_Scanner SHALL access the device camera
2. WHEN the camera detects a barcode, THE Barcode_Scanner SHALL decode the Product_Identifier
3. WHEN a Product_Identifier is decoded, Amigo SHALL display the barcode number to the user
4. IF camera access is denied, THEN Amigo SHALL display a message requesting camera permissions
5. IF barcode decoding fails, THEN Amigo SHALL prompt the user to adjust camera position or lighting
6. THE Barcode_Scanner SHALL support UPC and EAN barcode formats

### Requirement 34: Barcode API Integration

**User Story:** As a user, I want scanned barcodes to automatically identify products, so that I can log packaged foods without manual entry

#### Acceptance Criteria

1. WHEN a Product_Identifier is decoded, Amigo SHALL query Barcode_API to identify the Packaged_Food
2. WHEN Barcode_API returns product information, Amigo SHALL display the product name and brand
3. WHEN a Packaged_Food is identified, Amigo SHALL retrieve Nutritional_Data from the product information
4. IF Barcode_API does not recognize the Product_Identifier, THEN Amigo SHALL offer to search USDA_API as a fallback
5. IF Barcode_API request fails, THEN Amigo SHALL return a descriptive error message
6. Amigo SHALL cache Packaged_Food data to reduce API calls for frequently scanned items

### Requirement 35: Barcode-Based Meal Logging

**User Story:** As a user, I want to create meal logs by scanning barcodes, so that I can quickly log packaged foods with accurate nutritional data

#### Acceptance Criteria

1. WHEN a Packaged_Food is identified from a barcode scan, Amigo SHALL allow the user to specify serving quantity
2. WHEN the user confirms the serving quantity, Amigo SHALL create a Meal_Log with the Packaged_Food and Nutritional_Data
3. Amigo SHALL calculate total Nutritional_Data based on the serving quantity
4. WHEN a barcode-based Meal_Log is created, Amigo SHALL store the Product_Identifier with the log
5. Amigo SHALL display the Packaged_Food name, brand, and Nutritional_Data in the Meal_Log
6. WHEN a barcode-based Meal_Log is created, Amigo SHALL decrement the appropriate Usage_Quota based on subscription tier

### Requirement 36: AI Meal Logging Enhancement with USDA Data

**User Story:** As a user, I want my AI-logged meals enriched with USDA nutritional data, so that image, voice, and text logs have more accurate nutritional information

#### Acceptance Criteria

1. WHEN the Image_Logger identifies food items, THE Nutrition_Enricher SHALL query USDA_API for matching Food_Items
2. WHEN the Voice_Logger parses food items, THE Nutrition_Enricher SHALL query USDA_API for matching Food_Items
3. WHEN the Text_Logger parses food items, THE Nutrition_Enricher SHALL query USDA_API for matching Food_Items
4. WHEN USDA_API returns matching Food_Items, THE Nutrition_Enricher SHALL use the verified Nutritional_Data in the Meal_Log
5. IF USDA_API does not return matches, THEN Amigo SHALL use AI-estimated nutritional information
6. THE Nutrition_Enricher SHALL prioritize USDA_API data over AI estimates when both are available

### Requirement 37: Nutritional Data Accuracy Indicators

**User Story:** As a user, I want to know the source of nutritional information, so that I can understand the accuracy of my meal logs

#### Acceptance Criteria

1. WHEN a Meal_Log uses USDA_API data, Amigo SHALL display a "USDA Verified" indicator
2. WHEN a Meal_Log uses Barcode_API data, Amigo SHALL display a "Product Database" indicator
3. WHEN a Meal_Log uses AI-estimated data, Amigo SHALL display an "AI Estimate" indicator
4. Amigo SHALL allow users to view the data source for each Food_Item in a Meal_Log
5. Amigo SHALL display confidence levels for AI-estimated nutritional information

### Requirement 38: Manual Barcode Entry

**User Story:** As a user, I want to manually enter barcode numbers, so that I can log products when camera scanning is not available

#### Acceptance Criteria

1. Amigo SHALL provide a manual barcode entry interface
2. WHEN a user submits a manually entered Product_Identifier, Amigo SHALL validate the barcode format
3. WHEN the barcode format is valid, Amigo SHALL query Barcode_API to identify the Packaged_Food
4. IF the barcode format is invalid, THEN Amigo SHALL display a format error message
5. WHEN a Packaged_Food is identified from manual entry, Amigo SHALL proceed with the same flow as scanned barcodes

### Requirement 39: Offline Nutritional Data Access

**User Story:** As a user, I want access to previously retrieved nutritional data when offline, so that I can log meals without an internet connection

#### Acceptance Criteria

1. WHEN Nutritional_Data is retrieved from USDA_API or Barcode_API, Amigo SHALL cache the data locally
2. WHEN the device is offline, Amigo SHALL use cached Nutritional_Data for meal logging
3. WHEN the device is offline and cached data is unavailable, Amigo SHALL notify the user that nutritional data requires internet connection
4. WHEN the device reconnects, Amigo SHALL update cached Nutritional_Data for recently accessed Food_Items
5. Amigo SHALL indicate when displayed Nutritional_Data is from cache versus live API data

### Requirement 40: Custom Food Database

**User Story:** As a user, I want to save custom foods not found in USDA or barcode databases, so that I can log homemade or regional foods

#### Acceptance Criteria

1. WHEN a user cannot find a Food_Item in USDA_API or Barcode_API, Amigo SHALL allow creation of a custom Food_Item
2. WHEN creating a custom Food_Item, Amigo SHALL require the user to provide a name and basic Nutritional_Data
3. WHEN a custom Food_Item is created, THE Data_Store SHALL persist it for future use
4. Amigo SHALL allow users to edit or delete custom Food_Items
5. WHEN displaying search results, Amigo SHALL include both database Food_Items and custom Food_Items
6. Amigo SHALL indicate which Food_Items are custom versus from external databases

### Requirement 41: Barcode Scanning Usage Quota

**User Story:** As a user, I want barcode scanning to count toward my meal logging quota, so that the feature is available within my subscription tier

#### Acceptance Criteria

1. WHEN a barcode-based Meal_Log is created, Amigo SHALL treat it as equivalent to an image-based meal log for quota purposes
2. WHERE Free tier is active, Amigo SHALL allow barcode scanning within the image-based meal log quota
3. WHERE Pro tier is active, Amigo SHALL allow unlimited barcode scanning
4. WHEN a user exceeds their Usage_Quota, Amigo SHALL prevent additional barcode-based meal logs
5. Amigo SHALL display barcode scanning availability in the Usage_Quota interface

### Requirement 42: API Error Handling and Fallbacks

**User Story:** As a user, I want the app to handle API failures gracefully, so that I can continue logging meals even when external services are unavailable

#### Acceptance Criteria

1. IF USDA_API is unavailable, THEN Amigo SHALL use cached data or AI estimates as fallback
2. IF Barcode_API is unavailable, THEN Amigo SHALL allow manual food search via USDA_API
3. WHEN an API request times out, Amigo SHALL retry the request up to 3 times with exponential backoff
4. IF all API sources fail, THEN Amigo SHALL allow meal logging with AI-estimated Nutritional_Data
5. Amigo SHALL log all API errors for troubleshooting and monitoring
6. WHEN API services recover, Amigo SHALL automatically resume using external Nutritional_Data sources

### Requirement 43: Water Intake Logging

**User Story:** As a user, I want to log my water consumption throughout the day, so that I can track my hydration levels

#### Acceptance Criteria

1. WHEN a user logs water intake, THE Water_Tracker SHALL record the volume and timestamp in a Water_Log
2. THE Water_Tracker SHALL support volume entry in milliliters, ounces, cups, and liters
3. WHEN a Water_Log is created, THE Data_Store SHALL persist the data immediately
4. THE Water_Tracker SHALL allow users to log water intake with predefined quick-add amounts
5. THE Water_Tracker SHALL allow users to enter custom water volumes
6. WHEN a user creates a Water_Log, Amigo SHALL update the daily Hydration_Progress
7. THE Water_Tracker SHALL associate each Water_Log with the current date for daily tracking
8. Amigo SHALL allow users to edit or delete Water_Logs

### Requirement 44: Daily Water Intake Goals

**User Story:** As a user, I want to set a daily water intake goal, so that I can work toward healthy hydration levels

#### Acceptance Criteria

1. Amigo SHALL allow users to set a Water_Goal as a target daily volume
2. WHEN a user sets a Water_Goal, THE Water_Tracker SHALL validate the volume is greater than zero
3. THE Water_Tracker SHALL store the Water_Goal in the Data_Store
4. Amigo SHALL provide recommended Water_Goal values based on common health guidelines
5. Amigo SHALL allow users to modify their Water_Goal at any time
6. WHEN a Water_Goal is modified, Amigo SHALL apply the new goal to the current day immediately
7. IF no Water_Goal is set, THEN Amigo SHALL prompt the user to set a goal when first accessing water tracking

### Requirement 45: Hydration Progress Tracking

**User Story:** As a user, I want to see my progress toward my daily water goal, so that I know how much more water I need to drink

#### Acceptance Criteria

1. Amigo SHALL display current Hydration_Progress as total volume consumed for the day
2. Amigo SHALL display Hydration_Progress as a percentage of the Water_Goal
3. Amigo SHALL provide a visual indicator showing progress toward the Water_Goal
4. WHEN a user reaches their Water_Goal, Amigo SHALL display a completion indicator
5. Amigo SHALL reset daily Hydration_Progress at midnight local time
6. Amigo SHALL display remaining water volume needed to reach the Water_Goal

### Requirement 46: Water Intake Reminders

**User Story:** As a user, I want to receive reminders to drink water, so that I stay hydrated throughout the day

#### Acceptance Criteria

1. Amigo SHALL allow users to enable or disable Water_Reminders
2. WHEN Water_Reminders are enabled, Amigo SHALL allow users to set reminder frequency in hours
3. WHEN Water_Reminders are enabled, Amigo SHALL allow users to set active hours for reminders
4. WHEN a reminder time arrives, Amigo SHALL send a Water_Reminder notification to the user
5. Amigo SHALL not send Water_Reminders outside the user-configured active hours
6. WHEN a user logs water intake, Amigo SHALL reset the reminder timer
7. WHEN a user reaches their Water_Goal, Amigo SHALL stop sending Water_Reminders for that day

### Requirement 47: Hydration History and Trends

**User Story:** As a user, I want to view my water intake history, so that I can see my hydration patterns over time

#### Acceptance Criteria

1. Amigo SHALL display Hydration_History showing daily water intake for the past 7 days
2. Amigo SHALL display Hydration_History showing daily water intake for the past 30 days
3. Amigo SHALL display whether the Water_Goal was met for each day in Hydration_History
4. Amigo SHALL provide a visual chart showing water intake trends over time
5. Amigo SHALL calculate and display average daily water intake over selected time periods
6. Amigo SHALL allow users to view detailed Water_Logs for any historical date
7. WHERE Pro tier is active, Amigo SHALL provide extended Hydration_History beyond 30 days

### Requirement 48: Fasting Session Management

**User Story:** As a user, I want to start and stop fasting timers, so that I can track my intermittent fasting periods

#### Acceptance Criteria

1. WHEN a user starts a fast, THE Fasting_Tracker SHALL create a new Fasting_Session with the current timestamp
2. WHEN a Fasting_Session is active, THE Fasting_Timer SHALL display elapsed Fasting_Duration
3. THE Fasting_Timer SHALL update the displayed Fasting_Duration in real-time
4. WHEN a user stops a fast, THE Fasting_Tracker SHALL record the end timestamp and calculate total Fasting_Duration
5. WHEN a Fasting_Session is completed, THE Data_Store SHALL persist the Fasting_Session data
6. Amigo SHALL allow only one active Fasting_Session at a time
7. IF a user attempts to start a fast while one is active, THEN Amigo SHALL display an error message
8. Amigo SHALL allow users to cancel an active Fasting_Session

### Requirement 49: Fasting Protocol Support

**User Story:** As a user, I want to select from common fasting protocols, so that I can follow structured intermittent fasting schedules

#### Acceptance Criteria

1. Amigo SHALL support 16:8 Fasting_Protocol (16 hours fasting, 8 hours eating)
2. Amigo SHALL support 18:6 Fasting_Protocol (18 hours fasting, 6 hours eating)
3. Amigo SHALL support 20:4 Fasting_Protocol (20 hours fasting, 4 hours eating)
4. Amigo SHALL support custom Fasting_Protocol with user-defined fasting duration
5. WHEN a user selects a Fasting_Protocol, Amigo SHALL display the target Fasting_Duration
6. WHEN a Fasting_Session reaches the target Fasting_Duration, Amigo SHALL display a goal completion indicator
7. Amigo SHALL allow users to continue fasting beyond the target Fasting_Duration
8. Amigo SHALL store the selected Fasting_Protocol with each Fasting_Session

### Requirement 50: Fasting Duration Tracking

**User Story:** As a user, I want to see how long I've been fasting, so that I can monitor my progress toward my fasting goal

#### Acceptance Criteria

1. WHILE a Fasting_Session is active, THE Fasting_Timer SHALL display elapsed time in hours and minutes
2. THE Fasting_Timer SHALL display progress toward the target Fasting_Duration as a percentage
3. THE Fasting_Timer SHALL provide a visual indicator showing progress toward the fasting goal
4. WHEN a Fasting_Session reaches the target Fasting_Duration, Amigo SHALL display a completion message
5. Amigo SHALL display remaining time until the target Fasting_Duration is reached
6. THE Fasting_Timer SHALL continue tracking time even when Amigo is not actively open

### Requirement 51: Fasting History and Streaks

**User Story:** As a user, I want to view my fasting history and streaks, so that I can see my consistency with intermittent fasting

#### Acceptance Criteria

1. Amigo SHALL display Fasting_History showing all completed Fasting_Sessions in chronological order
2. Amigo SHALL display the Fasting_Duration for each completed Fasting_Session
3. Amigo SHALL display the Fasting_Protocol used for each Fasting_Session
4. Amigo SHALL calculate and display the current Fasting_Streak
5. THE Fasting_Tracker SHALL increment the Fasting_Streak when a user completes a Fasting_Session meeting their protocol goal
6. THE Fasting_Tracker SHALL reset the Fasting_Streak when a user misses a day without completing a fasting goal
7. Amigo SHALL display the longest Fasting_Streak achieved by the user
8. Amigo SHALL allow users to filter Fasting_History by date range

### Requirement 52: Fasting Completion Notifications

**User Story:** As a user, I want to receive notifications when my fasting period ends, so that I know when I can break my fast

#### Acceptance Criteria

1. WHEN a Fasting_Session reaches the target Fasting_Duration, Amigo SHALL send a Fasting_Notification
2. THE Fasting_Notification SHALL indicate that the fasting goal has been reached
3. Amigo SHALL allow users to enable or disable Fasting_Notifications
4. WHEN Fasting_Notifications are enabled, Amigo SHALL send notifications even when the app is not actively open
5. THE Fasting_Notification SHALL display the total Fasting_Duration achieved
6. Amigo SHALL allow users to configure notification sound and vibration preferences for Fasting_Notifications

### Requirement 53: Fasting and Meal Log Integration

**User Story:** As a user, I want my fasting sessions to integrate with my meal logs, so that I can see how my eating patterns relate to my fasting schedule

#### Acceptance Criteria

1. WHEN a user creates a Meal_Log, Amigo SHALL display whether the meal occurred during a fasting or eating window
2. WHEN a user logs a meal during an active Fasting_Session, Amigo SHALL prompt the user to confirm ending the fast
3. IF the user confirms ending the fast, THEN THE Fasting_Tracker SHALL complete the Fasting_Session with the meal timestamp
4. Amigo SHALL display Meal_Logs alongside Fasting_Sessions in the daily timeline view
5. Amigo SHALL calculate and display the time between the last meal and the start of a Fasting_Session

### Requirement 54: Amigo Fasting Coaching

**User Story:** As a Pro subscriber, I want Amigo to provide coaching based on my fasting patterns, so that I can optimize my intermittent fasting practice

#### Acceptance Criteria

1. WHERE Pro tier is active, THE AI_Coach SHALL access Fasting_History when generating coaching responses
2. WHEN Amigo provides coaching, THE AI_Coach SHALL reference Fasting_Sessions and Fasting_Streaks
3. THE AI_Coach SHALL correlate Fasting_Sessions with Meal_Logs to provide contextual fasting guidance
4. THE AI_Coach SHALL identify patterns in Fasting_Duration and completion rates
5. WHEN a user struggles to complete fasting goals, THE AI_Coach SHALL suggest adjustments to the Fasting_Protocol
6. WHEN a user achieves a significant Fasting_Streak, THE AI_Coach SHALL provide positive reinforcement
7. THE AI_Coach SHALL provide guidance on breaking fasts with appropriate meal choices

### Requirement 56: Fasting Statistics and Insights

**User Story:** As a user, I want to see statistics about my fasting practice, so that I can understand my fasting patterns and progress

#### Acceptance Criteria

1. Amigo SHALL calculate and display total number of completed Fasting_Sessions
2. Amigo SHALL calculate and display average Fasting_Duration across all sessions
3. Amigo SHALL calculate and display fasting completion rate as a percentage
4. Amigo SHALL display the longest single Fasting_Duration achieved
5. Amigo SHALL display weekly fasting frequency
6. WHERE Pro tier is active, Amigo SHALL provide monthly fasting trend analysis
7. WHERE Pro tier is active, Amigo SHALL correlate fasting patterns with weight trends when weight data is available

### Requirement 57: Water and Fasting Data Persistence

**User Story:** As a user, I want my water logs and fasting sessions saved securely, so that I don't lose my tracking history

#### Acceptance Criteria

1. WHEN a user creates a Water_Log, THE Data_Store SHALL persist the data immediately
2. WHEN a Fasting_Session is completed, THE Data_Store SHALL persist the session data immediately
3. WHEN Amigo restarts, THE Data_Store SHALL restore all Water_Logs and Fasting_Sessions
4. WHILE a Fasting_Session is active, THE Data_Store SHALL persist the session state to prevent data loss
5. IF a storage operation fails, THEN Amigo SHALL notify the user and retain the data in memory
6. THE Data_Store SHALL maintain data integrity for water and fasting data across application sessions

### Requirement 58: Water and Fasting Feature Access

**User Story:** As a user, I want water logging and fasting tracking available at all subscription tiers, so that I can track these health metrics regardless of my plan

#### Acceptance Criteria

1. Amigo SHALL provide water logging features to all subscription tiers
2. Amigo SHALL provide fasting tracking features to all subscription tiers
3. WHERE Free tier is active, Amigo SHALL provide basic water and fasting tracking without AI insights
4. WHERE Pro tier is active, THE Amigo SHALL provide personalized coaching based on water intake and fasting patterns
5. WHERE Pro tier is active, Amigo SHALL provide enhanced water and fasting statistics
6. Amigo SHALL not apply Usage_Quota limits to water logging or fasting tracking

### Requirement 59: First-Time User Onboarding

**User Story:** As a first-time user, I want a guided onboarding experience, so that I can quickly understand and set up the app

#### Acceptance Criteria

1. WHEN a user opens Amigo for the first time, THE Onboarding_Flow SHALL display welcome screens
2. THE Onboarding_Flow SHALL explain the core features of Amigo including meal logging, water tracking, and fasting
3. THE Onboarding_Flow SHALL explain Amigo and the AI coaching capabilities
4. THE Onboarding_Flow SHALL display the available Subscription_Tiers and their features
5. THE Onboarding_Flow SHALL allow users to skip the welcome screens and proceed directly to Profile_Setup
6. WHEN the welcome screens are completed, THE Onboarding_Flow SHALL proceed to Profile_Setup
7. Amigo SHALL not display the Onboarding_Flow to returning users

### Requirement 60: Profile Setup Wizard

**User Story:** As a new user, I want to set up my profile with basic information, so that the app can provide personalized guidance

#### Acceptance Criteria

1. WHEN Profile_Setup begins, Amigo SHALL prompt the user to enter their name
2. Amigo SHALL prompt the user to enter their age
3. Amigo SHALL prompt the user to enter their height with Unit_Preferences selection
4. Amigo SHALL prompt the user to enter their weight with Unit_Preferences selection
5. Amigo SHALL validate that age is a positive number between 13 and 120
6. Amigo SHALL validate that height and weight are positive numbers within reasonable ranges
7. WHEN profile information is complete, THE Data_Store SHALL persist the user profile data
8. Amigo SHALL allow users to skip optional profile fields
9. IF required profile fields are missing, THEN Amigo SHALL prevent completion of Profile_Setup

### Requirement 61: Health Goal Setting

**User Story:** As a new user, I want to set my health goals during onboarding, so that the app can track my progress toward those goals

#### Acceptance Criteria

1. WHEN Profile_Setup is complete, THE Onboarding_Flow SHALL prompt the user to set a Water_Goal
2. THE Onboarding_Flow SHALL prompt the user to select a Fasting_Protocol or skip fasting setup
3. THE Onboarding_Flow SHALL prompt the user to set daily calorie or nutrition goals
4. Amigo SHALL provide recommended goal values based on the user's age, height, weight, and activity level
5. Amigo SHALL allow users to customize recommended goals
6. WHEN health goals are set, THE Data_Store SHALL persist the goal data
7. Amigo SHALL allow users to skip goal setting and configure goals later

### Requirement 62: Feature Tutorial and Walkthrough

**User Story:** As a new user, I want an interactive tutorial of key features, so that I understand how to use the app effectively

#### Acceptance Criteria

1. WHEN Profile_Setup is complete, THE Onboarding_Flow SHALL offer an interactive Tutorial
2. THE Tutorial SHALL demonstrate how to log meals using image, voice, and text methods
3. THE Tutorial SHALL demonstrate how to log water intake
4. THE Tutorial SHALL demonstrate how to start and stop a Fasting_Session
5. THE Tutorial SHALL demonstrate how to access the Dashboard
6. WHERE Pro tier is active, THE Tutorial SHALL demonstrate how to chat with Amigo
7. Amigo SHALL allow users to skip the Tutorial
8. Amigo SHALL provide access to the Tutorial from app settings for returning users

### Requirement 63: Device Permission Requests

**User Story:** As a new user, I want to be asked for necessary device permissions, so that I understand why the app needs access to certain features

#### Acceptance Criteria

1. WHEN the Tutorial is complete, THE Onboarding_Flow SHALL request camera permission with an explanation
2. Amigo SHALL explain that camera access is needed for food photo logging and barcode scanning
3. THE Onboarding_Flow SHALL request notification permission with an explanation
4. Amigo SHALL explain that notifications are used for water reminders and fasting alerts
5. WHERE iOS platform is active, THE Onboarding_Flow SHALL request Apple Health permission with an explanation
6. WHERE Android platform is active, THE Onboarding_Flow SHALL request Google Health Connect permission with an explanation
7. Amigo SHALL allow users to decline permissions and continue using the app with limited functionality
8. IF a user declines a permission, THEN Amigo SHALL explain which features will be unavailable
9. Amigo SHALL provide instructions for enabling permissions later in device settings

### Requirement 64: Onboarding Completion

**User Story:** As a new user, I want to complete onboarding and start using the app, so that I can begin tracking my health

#### Acceptance Criteria

1. WHEN all Onboarding_Flow steps are complete, Amigo SHALL display a completion message
2. WHEN onboarding is complete, Amigo SHALL navigate to the Dashboard
3. Amigo SHALL mark the user account as having completed onboarding
4. WHEN a user reopens Amigo, Amigo SHALL navigate directly to the Dashboard without showing Onboarding_Flow
5. Amigo SHALL allow users to exit Onboarding_Flow at any time and resume later
6. IF a user exits Onboarding_Flow before completion, THEN Amigo SHALL resume from the last completed step on next launch

### Requirement 65: Payment Gateway Integration

**User Story:** As a user, I want to purchase subscriptions securely, so that I can access Pro features

#### Acceptance Criteria

1. Amigo SHALL integrate with a Payment_Gateway for processing subscription transactions
2. Amigo SHALL transmit all payment data over secure encrypted connections
3. Amigo SHALL not store credit card numbers or sensitive payment information locally
4. WHEN Payment_Gateway integration fails, Amigo SHALL display a descriptive error message
5. Amigo SHALL comply with PCI DSS standards for payment processing
6. THE Payment_Gateway SHALL support credit cards, debit cards, and digital wallets

### Requirement 66: Subscription Purchase Flow

**User Story:** As a user, I want to purchase a Pro subscription, so that I can access enhanced features

#### Acceptance Criteria

1. WHEN a user selects a Subscription_Tier to purchase, Amigo SHALL display the subscription price and billing frequency
2. Amigo SHALL display a detailed list of features included in the selected Subscription_Tier
3. WHEN a user confirms the purchase, Amigo SHALL redirect to the Payment_Gateway for payment processing
4. WHEN payment processing succeeds, Amigo SHALL update the user's Subscription_Tier immediately
5. WHEN the Subscription_Tier is updated, Amigo SHALL grant access to tier-specific features
6. IF payment processing fails, THEN Amigo SHALL display an error message and retain the current Subscription_Tier
7. Amigo SHALL support monthly and annual billing frequencies
8. Amigo SHALL display cost savings for annual subscriptions compared to monthly

### Requirement 67: Payment Method Management

**User Story:** As a subscriber, I want to manage my payment methods, so that I can update my billing information

#### Acceptance Criteria

1. Amigo SHALL allow users to view their stored Payment_Methods
2. Amigo SHALL display the last four digits and type of each stored Payment_Method
3. Amigo SHALL allow users to add new Payment_Methods through the Payment_Gateway
4. Amigo SHALL allow users to remove stored Payment_Methods
5. Amigo SHALL allow users to set a default Payment_Method for subscription renewals
6. WHEN a Payment_Method is updated, Amigo SHALL use the new method for the next billing cycle
7. IF a Payment_Method fails during renewal, THEN Amigo SHALL notify the user to update their payment information

### Requirement 68: Subscription Renewal and Cancellation

**User Story:** As a subscriber, I want to manage my subscription renewal, so that I can control my billing

#### Acceptance Criteria

1. THE Health_App SHALL automatically renew subscriptions at the end of each billing period
2. WHEN a subscription renewal succeeds, THE Health_App SHALL maintain the current Subscription_Tier
3. THE Health_App SHALL allow users to cancel their subscription at any time
4. WHEN a user cancels their subscription, THE Health_App SHALL maintain tier access until the end of the current billing period
5. WHEN the billing period ends after cancellation, THE Health_App SHALL downgrade the user to Free tier
6. THE Health_App SHALL display the next renewal date and amount for active subscriptions
7. THE Health_App SHALL send a notification 3 days before subscription renewal
8. IF subscription renewal fails, THEN THE Health_App SHALL retry payment and notify the user

### Requirement 69: Receipt and Invoice Generation

**User Story:** As a subscriber, I want to receive receipts for my purchases, so that I have records of my transactions

#### Acceptance Criteria

1. WHEN a Subscription_Purchase succeeds, THE Health_App SHALL generate a Receipt
2. THE Receipt SHALL include the transaction date, amount, Subscription_Tier, and billing period
3. THE Health_App SHALL send the Receipt to the user's registered email address
4. THE Health_App SHALL allow users to view past Receipts in the app
5. THE Health_App SHALL allow users to download Receipts as PDF files
6. THE Health_App SHALL generate Receipts for subscription renewals
7. THE Receipt SHALL include the Payment_Gateway transaction identifier

### Requirement 70: Trial Period Support

**User Story:** As a new user, I want to try Pro features with a trial period, so that I can evaluate the subscription before paying

#### Acceptance Criteria

1. WHERE Trial_Period is offered, THE Health_App SHALL allow new users to activate Pro tier features without immediate payment
2. WHEN a user starts a Trial_Period, THE Health_App SHALL display the trial duration and end date
3. WHEN a user starts a Trial_Period, THE Health_App SHALL require a Payment_Method for automatic billing after trial
4. WHILE Trial_Period is active, THE Health_App SHALL provide full access to the trial tier features
5. THE Health_App SHALL send notifications 3 days and 1 day before Trial_Period ends
6. WHEN Trial_Period ends, THE Health_App SHALL automatically charge the Payment_Method and continue the subscription
7. THE Health_App SHALL allow users to cancel during Trial_Period without being charged
8. THE Health_App SHALL limit Trial_Period to one per user account

### Requirement 71: Purchase Restoration

**User Story:** As a user who reinstalls the app or switches devices, I want to restore my previous purchases, so that I don't lose access to my subscription

#### Acceptance Criteria

1. THE Health_App SHALL provide a Purchase_Restoration feature accessible from settings
2. WHEN a user initiates Purchase_Restoration, THE Health_App SHALL query the Payment_Gateway for previous purchases
3. WHEN previous purchases are found, THE Health_App SHALL restore the user's Subscription_Tier
4. WHEN the Subscription_Tier is restored, THE Health_App SHALL grant access to tier-specific features immediately
5. IF no previous purchases are found, THEN THE Health_App SHALL display a message indicating no purchases to restore
6. THE Health_App SHALL restore purchases across devices when the user signs in with the same account
7. WHERE iOS platform is active, THE Health_App SHALL use App Store purchase restoration
8. WHERE Android platform is active, THE Health_App SHALL use Google Play purchase restoration

### Requirement 72: Push Notification Infrastructure

**User Story:** As a user, I want to receive notifications from the app, so that I stay informed about my health tracking

#### Acceptance Criteria

1. THE Health_App SHALL integrate with platform notification services for sending Push_Notifications
2. WHERE iOS platform is active, THE Health_App SHALL use Apple Push Notification Service
3. WHERE Android platform is active, THE Health_App SHALL use Firebase Cloud Messaging
4. THE Health_App SHALL request notification permission before sending Push_Notifications
5. IF notification permission is denied, THEN THE Health_App SHALL disable notification features and inform the user
6. THE Health_App SHALL handle notification delivery failures gracefully
7. THE Health_App SHALL respect system-level notification settings

### Requirement 73: Notification Preferences and Settings

**User Story:** As a user, I want to control which notifications I receive, so that I only get alerts that are useful to me

#### Acceptance Criteria

1. THE Health_App SHALL provide Notification_Preferences in the settings menu
2. THE Notification_Preferences SHALL allow users to enable or disable Water_Reminders
3. THE Notification_Preferences SHALL allow users to enable or disable Fasting_Notifications
4. THE Notification_Preferences SHALL allow users to enable or disable coaching prompts from Amigo
5. THE Notification_Preferences SHALL allow users to enable or disable subscription and billing notifications
6. THE Notification_Preferences SHALL allow users to set quiet hours during which no notifications are sent
7. WHEN a user disables a notification type, THE Health_App SHALL stop sending that type of notification
8. THE Health_App SHALL persist Notification_Preferences across app sessions

### Requirement 74: Notification Scheduling and Delivery

**User Story:** As a user, I want notifications delivered at the right times, so that they are helpful and not disruptive

#### Acceptance Criteria

1. THE Notification_Scheduler SHALL schedule Push_Notifications based on user-configured times and preferences
2. THE Notification_Scheduler SHALL respect quiet hours configured in Notification_Preferences
3. WHEN a scheduled notification time arrives, THE Notification_Scheduler SHALL deliver the Push_Notification
4. THE Notification_Scheduler SHALL handle timezone changes and maintain correct notification timing
5. THE Notification_Scheduler SHALL cancel scheduled notifications when the triggering condition is no longer valid
6. WHEN the Health_App is uninstalled, THE Notification_Scheduler SHALL cancel all pending notifications
7. THE Notification_Scheduler SHALL batch multiple notifications to avoid overwhelming the user

### Requirement 75: Coaching Prompt Notifications

**User Story:** As a Pro subscriber, I want to receive coaching prompts from Amigo, so that I stay engaged with my health goals

#### Acceptance Criteria

1. WHERE Pro tier is active, THE Health_App SHALL send coaching prompt notifications from Amigo
2. THE AI_Coach SHALL generate personalized coaching prompts based on user behavior and goals
3. THE Health_App SHALL send coaching prompts at optimal times based on user activity patterns
4. THE coaching prompt notification SHALL include a brief message from Amigo
5. WHEN a user taps a coaching prompt notification, THE Health_App SHALL open the Chat_Interface with Amigo
6. THE Health_App SHALL limit coaching prompts to a maximum frequency to avoid notification fatigue
7. WHERE Free tier is active, THE Health_App SHALL not send coaching prompt notifications

### Requirement 76: Deep Linking from Notifications

**User Story:** As a user, I want notifications to take me directly to relevant features, so that I can quickly act on the notification

#### Acceptance Criteria

1. WHEN a user taps a Water_Reminder notification, THE Health_App SHALL open to the water logging interface
2. WHEN a user taps a Fasting_Notification, THE Health_App SHALL open to the Fasting_Timer display
3. WHEN a user taps a coaching prompt notification, THE Health_App SHALL open to the Chat_Interface with Amigo
4. WHEN a user taps a subscription notification, THE Health_App SHALL open to the subscription management screen
5. THE Health_App SHALL handle Deep_Links correctly even when the app is not running
6. THE Health_App SHALL handle Deep_Links correctly when the app is running in the background
7. IF a Deep_Link target requires authentication, THEN THE Health_App SHALL authenticate the user before navigating

### Requirement 77: Dashboard Overview

**User Story:** As a user, I want a main dashboard showing my daily health summary, so that I can see my progress at a glance

#### Acceptance Criteria

1. WHEN a user opens the Health_App, THE Dashboard SHALL display as the main home screen
2. THE Dashboard SHALL display the current date
3. THE Dashboard SHALL display a Daily_Summary of all tracked health metrics
4. THE Dashboard SHALL provide quick access buttons for meal logging, water logging, and fasting
5. THE Dashboard SHALL display the user's current Subscription_Tier
6. THE Dashboard SHALL refresh data when the user pulls down to refresh
7. THE Dashboard SHALL load within 2 seconds on typical network conditions

### Requirement 78: Daily Progress Display

**User Story:** As a user, I want to see my daily progress for all tracked metrics, so that I know how I'm doing toward my goals

#### Acceptance Criteria

1. THE Dashboard SHALL display Hydration_Progress toward the Water_Goal
2. THE Dashboard SHALL display daily calorie intake and remaining calories toward nutrition goals
3. THE Dashboard SHALL display macronutrient breakdown for the current day
4. THE Dashboard SHALL display active Fasting_Session status and elapsed time
5. THE Dashboard SHALL display the number of meals logged today
6. THE Dashboard SHALL display step count and exercise data from connected Health_Platforms
7. THE Dashboard SHALL use visual progress indicators such as progress bars or circular charts
8. WHEN a daily goal is achieved, THE Dashboard SHALL display a completion indicator

### Requirement 79: Recent Activity Feed

**User Story:** As a user, I want to see my recent health activities, so that I can review what I've logged today

#### Acceptance Criteria

1. THE Dashboard SHALL display an Activity_Feed showing recent user actions
2. THE Activity_Feed SHALL display recent Meal_Logs with timestamps and food photos when available
3. THE Activity_Feed SHALL display recent Water_Logs with volumes and timestamps
4. THE Activity_Feed SHALL display completed Fasting_Sessions with durations
5. THE Activity_Feed SHALL display items in reverse chronological order
6. THE Activity_Feed SHALL allow users to tap items to view details or edit entries
7. THE Activity_Feed SHALL display at least the 5 most recent activities
8. THE Health_App SHALL allow users to expand the Activity_Feed to view more historical entries

### Requirement 80: Quick Stats and Insights

**User Story:** As a user, I want to see key health statistics on my dashboard, so that I can track my overall progress

#### Acceptance Criteria

1. THE Dashboard SHALL display Quick_Stats including current Fasting_Streak
2. THE Quick_Stats SHALL display weekly average water intake
3. THE Quick_Stats SHALL display weekly meal logging consistency
4. THE Quick_Stats SHALL display total meals logged this week
5. WHERE Pro tier is active, THE Quick_Stats SHALL display AI-generated insights about health trends
6. WHERE Pro tier is active, THE Quick_Stats SHALL display personalized tips from Amigo
7. THE Quick_Stats SHALL update in real-time as the user logs new data

### Requirement 81: Dashboard Navigation

**User Story:** As a user, I want easy navigation to all major features from the dashboard, so that I can quickly access what I need

#### Acceptance Criteria

1. THE Dashboard SHALL provide a navigation menu to access all major app features
2. THE Dashboard SHALL provide quick action buttons for logging meals via image, voice, or text
3. THE Dashboard SHALL provide a quick action button for logging water intake
4. THE Dashboard SHALL provide a quick action button for starting or stopping a Fasting_Session
5. THE Dashboard SHALL provide navigation to meal history and detailed nutritional analysis
6. THE Dashboard SHALL provide navigation to water and fasting history
7. THE Dashboard SHALL provide navigation to settings and account management
8. WHERE Pro tier is active, THE Dashboard SHALL provide quick access to chat with Amigo
9. THE Dashboard SHALL use clear icons and labels for all navigation elements

### Requirement 82: User Data Export

**User Story:** As a user, I want to export my health data, so that I can keep personal records or use the data elsewhere

#### Acceptance Criteria

1. THE Health_App SHALL provide a Data_Export feature accessible from settings
2. WHEN a user initiates Data_Export, THE Health_App SHALL allow selection of data types to export
3. THE Health_App SHALL support JSON Export_Format for structured data
4. THE Health_App SHALL support CSV Export_Format for spreadsheet compatibility
5. THE Data_Export SHALL include all Meal_Logs with timestamps and nutritional information
6. THE Data_Export SHALL include all Water_Logs with timestamps and volumes
7. THE Data_Export SHALL include all Fasting_Sessions with start times, end times, and durations
8. THE Data_Export SHALL include user profile information and health goals
9. THE Data_Export SHALL include Health_Metrics from connected Health_Platforms
10. WHEN Data_Export is complete, THE Health_App SHALL allow the user to save or share the export file

### Requirement 83: GDPR Data Portability Compliance

**User Story:** As a user in the EU, I want my data export to comply with GDPR, so that I can exercise my data portability rights

#### Acceptance Criteria

1. THE Health_App SHALL provide GDPR_Compliance for data portability as required by European regulations
2. THE Data_Export SHALL include all personal data stored by the Health_App in a machine-readable format
3. THE Health_App SHALL complete Data_Export requests within 30 days as required by GDPR
4. THE Data_Export SHALL include metadata about data collection dates and sources
5. THE Health_App SHALL provide clear documentation explaining the exported data structure
6. THE Health_App SHALL not charge fees for Data_Export requests

### Requirement 84: Account Deletion and Data Removal

**User Story:** As a user, I want to delete my account and all associated data, so that I can remove my information from the system

#### Acceptance Criteria

1. THE Health_App SHALL provide an Account_Deletion feature accessible from settings
2. WHEN a user initiates Account_Deletion, THE Health_App SHALL display a confirmation warning about data loss
3. WHEN a user confirms Account_Deletion, THE Health_App SHALL require authentication to verify identity
4. WHEN Account_Deletion is confirmed, THE Health_App SHALL delete the User_Account
5. THE Health_App SHALL delete all Meal_Logs, Water_Logs, and Fasting_Sessions associated with the account
6. THE Health_App SHALL delete all Health_Metrics and synced data from Health_Platforms
7. THE Health_App SHALL delete all user profile information and preferences
8. THE Health_App SHALL revoke all OAuth_Tokens for connected Health_Platforms
9. THE Health_App SHALL cancel active subscriptions during Account_Deletion
10. WHEN Account_Deletion is complete, THE Health_App SHALL sign out the user and return to the login screen
11. THE Health_App SHALL complete Account_Deletion within 30 days as required by GDPR

### Requirement 85: Privacy Policy and Terms Acceptance

**User Story:** As a new user, I want to review and accept the privacy policy and terms of service, so that I understand how my data will be used

#### Acceptance Criteria

1. WHEN a user creates an account, THE Health_App SHALL display the Privacy_Policy and Terms_of_Service
2. THE Health_App SHALL require users to accept the Privacy_Policy before creating an account
3. THE Health_App SHALL require users to accept the Terms_of_Service before creating an account
4. THE Health_App SHALL provide links to view the full Privacy_Policy and Terms_of_Service documents
5. THE Health_App SHALL record the date and version of accepted Privacy_Policy and Terms_of_Service
6. WHEN Privacy_Policy or Terms_of_Service are updated, THE Health_App SHALL notify existing users
7. THE Health_App SHALL require users to accept updated Privacy_Policy or Terms_of_Service to continue using the app

### Requirement 86: Data Retention Policies

**User Story:** As a user, I want to understand how long my data is retained, so that I know my privacy is protected

#### Acceptance Criteria

1. THE Health_App SHALL implement Data_Retention_Policy governing how long user data is stored
2. THE Health_App SHALL retain active user data indefinitely while the account is active
3. WHEN an account is deleted, THE Health_App SHALL permanently delete all user data within 30 days
4. THE Health_App SHALL retain anonymized analytics data for service improvement
5. THE Health_App SHALL not retain identifiable user data after Account_Deletion
6. THE Health_App SHALL document Data_Retention_Policy in the Privacy_Policy
7. THE Health_App SHALL comply with regional data retention regulations

### Requirement 87: Unit Preferences Configuration

**User Story:** As a user, I want to choose my preferred measurement units, so that the app displays data in familiar formats

#### Acceptance Criteria

1. THE Health_App SHALL provide Unit_Preferences in the settings menu
2. THE Unit_Preferences SHALL allow users to select between metric and imperial systems
3. WHEN metric is selected, THE Health_App SHALL display weight in kilograms, height in centimeters, and volume in milliliters
4. WHEN imperial is selected, THE Health_App SHALL display weight in pounds, height in feet and inches, and volume in ounces
5. WHEN Unit_Preferences change, THE Health_App SHALL convert and redisplay all existing data in the new units
6. THE Health_App SHALL persist Unit_Preferences across app sessions
7. THE Health_App SHALL apply Unit_Preferences to all data entry and display throughout the app

### Requirement 88: Language Settings

**User Story:** As a user, I want to choose my preferred language, so that I can use the app in my native language

#### Acceptance Criteria

1. THE Health_App SHALL provide Language_Settings in the settings menu
2. THE Health_App SHALL support English as the default language
3. THE Health_App SHALL support additional languages based on target markets
4. WHEN a user changes Language_Settings, THE Health_App SHALL update all interface text immediately
5. THE Health_App SHALL persist Language_Settings across app sessions
6. THE Health_App SHALL detect and suggest the device's system language on first launch
7. WHERE Pro tier is active, THE AI_Coach SHALL respond to users in their selected language

### Requirement 89: Theme and Appearance Settings

**User Story:** As a user, I want to customize the app's appearance, so that it matches my visual preferences

#### Acceptance Criteria

1. THE Health_App SHALL provide Theme_Settings in the settings menu
2. THE Theme_Settings SHALL support light mode with bright backgrounds and dark text
3. THE Theme_Settings SHALL support dark mode with dark backgrounds and light text
4. THE Theme_Settings SHALL support automatic theme switching based on system settings
5. WHEN a user changes Theme_Settings, THE Health_App SHALL update the visual appearance immediately
6. THE Health_App SHALL persist Theme_Settings across app sessions
7. THE Health_App SHALL ensure all text remains readable in both light and dark modes
8. THE Health_App SHALL apply the selected theme to all screens and components

### Requirement 90: Account Settings Management

**User Story:** As a user, I want to manage my account settings, so that I can update my profile and preferences

#### Acceptance Criteria

1. THE Health_App SHALL provide account settings accessible from the settings menu
2. THE account settings SHALL allow users to view and edit their profile information
3. THE account settings SHALL allow users to change their email address with verification
4. THE account settings SHALL allow users to change their password with current password verification
5. THE account settings SHALL display the user's current Subscription_Tier and billing information
6. THE account settings SHALL provide access to subscription management features
7. THE account settings SHALL provide access to connected Health_Platforms management
8. THE account settings SHALL display the account creation date

### Requirement 91: Help and Support Section

**User Story:** As a user, I want access to help and support resources, so that I can resolve issues and learn how to use features

#### Acceptance Criteria

1. THE Health_App SHALL provide a help and support section accessible from the settings menu
2. THE help section SHALL include a searchable FAQ covering common questions
3. THE help section SHALL provide tutorials and guides for key features
4. THE help section SHALL provide contact information for customer support
5. THE help section SHALL allow users to submit support tickets or feedback
6. THE help section SHALL display app version information for troubleshooting
7. THE help section SHALL provide links to Privacy_Policy and Terms_of_Service
8. THE help section SHALL provide links to community forums or social media channels

### Requirement 92: About and App Information

**User Story:** As a user, I want to view information about the app, so that I know the version and can access legal documents

#### Acceptance Criteria

1. THE Health_App SHALL provide an about section accessible from the settings menu
2. THE about section SHALL display the app name and version number
3. THE about section SHALL display the build number for technical support purposes
4. THE about section SHALL provide links to Privacy_Policy, Terms_of_Service, and licenses
5. THE about section SHALL display copyright and attribution information
6. THE about section SHALL provide a link to rate the app on the app store
7. THE about section SHALL display information about third-party libraries and services used

### Requirement 93: Crash Reporting Integration

**User Story:** As a developer, I want to capture crash reports, so that I can identify and fix bugs quickly

#### Acceptance Criteria

1. THE Health_App SHALL integrate with a Crash_Reporter service for capturing application crashes
2. WHEN the Health_App crashes, THE Crash_Reporter SHALL capture the stack trace and device information
3. THE Crash_Reporter SHALL capture the app state and user actions leading to the crash
4. THE Crash_Reporter SHALL transmit crash reports over secure connections
5. THE Crash_Reporter SHALL anonymize user data in crash reports to protect privacy
6. THE Health_App SHALL allow users to opt out of crash reporting in privacy settings
7. THE Crash_Reporter SHALL not capture sensitive health data or authentication credentials

### Requirement 94: Error Logging for Debugging

**User Story:** As a developer, I want to log errors and diagnostic information, so that I can troubleshoot issues

#### Acceptance Criteria

1. THE Health_App SHALL implement an Error_Logger for recording error events
2. THE Error_Logger SHALL capture error messages, timestamps, and context information
3. THE Error_Logger SHALL log API failures with request and response details
4. THE Error_Logger SHALL log authentication failures and security events
5. THE Error_Logger SHALL log data synchronization errors
6. THE Error_Logger SHALL categorize errors by severity level
7. THE Error_Logger SHALL transmit error logs to a monitoring service for analysis
8. THE Error_Logger SHALL respect user privacy and not log sensitive personal information

### Requirement 95: User-Friendly Error Messages

**User Story:** As a user, I want clear error messages when something goes wrong, so that I understand what happened and how to fix it

#### Acceptance Criteria

1. WHEN an error occurs, THE Health_App SHALL display an Error_Message to the user
2. THE Error_Message SHALL explain what went wrong in plain language without technical jargon
3. THE Error_Message SHALL provide actionable steps to resolve the issue when possible
4. THE Error_Message SHALL avoid exposing technical details or stack traces to users
5. THE Error_Message SHALL be contextual and relevant to the user's current action
6. WHEN an error is temporary, THE Error_Message SHALL indicate the user can retry
7. WHEN an error requires support, THE Error_Message SHALL provide a way to contact support
8. THE Health_App SHALL log the technical error details while showing simplified messages to users

### Requirement 96: Network Connectivity Handling

**User Story:** As a user, I want the app to handle network issues gracefully, so that I can continue using offline features

#### Acceptance Criteria

1. THE Health_App SHALL implement a Network_Handler to monitor connectivity status
2. WHEN the device loses network connectivity, THE Network_Handler SHALL detect the change
3. WHEN offline, THE Health_App SHALL display a connectivity status indicator
4. WHEN offline, THE Health_App SHALL allow users to log meals, water, and fasting using cached data
5. WHEN offline, THE Health_App SHALL queue data for synchronization when connectivity returns
6. WHEN connectivity is restored, THE Network_Handler SHALL automatically sync queued data
7. THE Health_App SHALL notify users when features require internet connectivity
8. THE Health_App SHALL not display error messages for expected offline behavior

### Requirement 97: Graceful Service Degradation

**User Story:** As a user, I want the app to continue working with reduced functionality when services are unavailable, so that I can still track my health

#### Acceptance Criteria

1. THE Health_App SHALL implement Graceful_Degradation when external services are unavailable
2. WHEN USDA_API is unavailable, THE Health_App SHALL use cached nutritional data and AI estimates
3. WHEN Barcode_API is unavailable, THE Health_App SHALL allow manual food search as a fallback
4. WHEN Payment_Gateway is unavailable, THE Health_App SHALL maintain current subscription access
5. WHEN Health_Platform APIs are unavailable, THE Health_App SHALL display last synced data
6. WHEN AI_Coach services are unavailable, THE Health_App SHALL display a service unavailable message
7. THE Health_App SHALL inform users when operating with reduced functionality
8. WHEN services are restored, THE Health_App SHALL automatically resume full functionality
9. THE Health_App SHALL not prevent core features like meal logging when external services fail

### Requirement 98: Service Health Monitoring

**User Story:** As a developer, I want to monitor service health, so that I can proactively address issues

#### Acceptance Criteria

1. THE Health_App SHALL monitor the availability of critical external services
2. THE Health_App SHALL track API response times and error rates
3. THE Health_App SHALL report service health metrics to a monitoring dashboard
4. WHEN service error rates exceed thresholds, THE Health_App SHALL trigger alerts
5. THE Health_App SHALL track user-impacting errors separately from background errors
6. THE Health_App SHALL monitor subscription and payment processing success rates
7. THE Health_App SHALL track data synchronization success rates for Health_Platforms
8. THE Health_App SHALL provide service health status in the developer dashboard


### Requirement 99: Supabase Backend Integration

**User Story:** As a developer, I want to use Supabase as the backend infrastructure, so that the app has a scalable and secure foundation

#### Acceptance Criteria

1. THE Health_App SHALL integrate with Supabase for all backend services
2. THE Health_App SHALL use Supabase_Auth for user authentication and session management
3. THE Health_App SHALL use Supabase_Database for storing all user data and health records
4. THE Health_App SHALL use Supabase_Storage for storing Food_Photos and media files
5. THE Health_App SHALL use Supabase_Realtime for live data synchronization
6. THE Health_App SHALL store Supabase connection credentials securely
7. THE Health_App SHALL transmit all Supabase API requests over secure connections
8. WHEN Supabase services are unavailable, THE Health_App SHALL implement Graceful_Degradation

### Requirement 100: Supabase Authentication Integration

**User Story:** As a user, I want my authentication handled by Supabase, so that my account is secure and reliable

#### Acceptance Criteria

1. THE Email_Authenticator SHALL use Supabase_Auth for email and password authentication
2. THE Google_Authenticator SHALL use Supabase_Auth for Google OAuth authentication
3. THE Apple_Authenticator SHALL use Supabase_Auth for Apple OAuth authentication
4. WHEN a user authenticates successfully, THE Supabase_Auth SHALL generate a Session_Token
5. THE Supabase_Auth SHALL manage Session_Token expiration and refresh automatically
6. THE Supabase_Auth SHALL enforce password security requirements
7. THE Supabase_Auth SHALL handle password reset flows
8. WHEN a user signs out, THE Supabase_Auth SHALL invalidate the Session_Token

### Requirement 101: Supabase Database Schema for User Accounts

**User Story:** As a developer, I want user accounts stored in Supabase Database, so that user data is centrally managed

#### Acceptance Criteria

1. THE Supabase_Database SHALL contain a Database_Table for User_Accounts
2. THE User_Account table SHALL store user profile information including name, age, height, and weight
3. THE User_Account table SHALL store Unit_Preferences, Language_Settings, and Theme_Settings
4. THE User_Account table SHALL store health goals including Water_Goal and nutrition targets
5. THE User_Account table SHALL store Notification_Preferences
6. THE User_Account table SHALL associate each record with a Supabase_Auth user identifier
7. THE Supabase_Database SHALL enforce data type constraints and validation rules
8. THE Supabase_Database SHALL create indexes for efficient user data queries

### Requirement 102: Supabase Database Schema for Meal Logs

**User Story:** As a developer, I want meal logs stored in Supabase Database, so that meal data is persistent and queryable

#### Acceptance Criteria

1. THE Supabase_Database SHALL contain a Database_Table for Meal_Logs
2. THE Meal_Log table SHALL store meal timestamp, input method, and nutritional information
3. THE Meal_Log table SHALL store food item names, quantities, and data sources
4. THE Meal_Log table SHALL reference Food_Photos stored in Supabase_Storage
5. THE Meal_Log table SHALL associate each record with a User_Account
6. THE Supabase_Database SHALL enforce foreign key constraints between Meal_Logs and User_Accounts
7. THE Supabase_Database SHALL create indexes for efficient meal history queries by user and date
8. THE Meal_Log table SHALL store Product_Identifiers for barcode-scanned items

### Requirement 103: Supabase Database Schema for Water and Fasting

**User Story:** As a developer, I want water logs and fasting sessions stored in Supabase Database, so that tracking data is persistent

#### Acceptance Criteria

1. THE Supabase_Database SHALL contain a Database_Table for Water_Logs
2. THE Water_Log table SHALL store volume, timestamp, and unit of measurement
3. THE Water_Log table SHALL associate each record with a User_Account
4. THE Supabase_Database SHALL contain a Database_Table for Fasting_Sessions
5. THE Fasting_Session table SHALL store start timestamp, end timestamp, Fasting_Protocol, and Fasting_Duration
6. THE Fasting_Session table SHALL store whether the session was completed successfully
7. THE Fasting_Session table SHALL associate each record with a User_Account
8. THE Supabase_Database SHALL create indexes for efficient water and fasting history queries

### Requirement 104: Supabase Database Schema for Subscriptions

**User Story:** As a developer, I want subscription data stored in Supabase Database, so that subscription status is tracked reliably

#### Acceptance Criteria

1. THE Supabase_Database SHALL contain a Database_Table for Subscription_Records
2. THE Subscription_Record table SHALL store current Subscription_Tier for each user
3. THE Subscription_Record table SHALL store subscription start date, renewal date, and billing frequency
4. THE Subscription_Record table SHALL store Usage_Quota for each meal logging method
5. THE Subscription_Record table SHALL store Trial_Period status and expiration date
6. THE Subscription_Record table SHALL associate each record with a User_Account
7. THE Supabase_Database SHALL contain a Database_Table for Payment_Transactions
8. THE Payment_Transaction table SHALL store transaction date, amount, status, and Payment_Gateway reference
9. THE Supabase_Database SHALL enforce data integrity for subscription and payment records

### Requirement 105: Supabase Database Schema for Health Metrics

**User Story:** As a developer, I want health metrics from connected platforms stored in Supabase Database, so that synced data is available for analysis

#### Acceptance Criteria

1. THE Supabase_Database SHALL contain a Database_Table for Health_Metrics
2. THE Health_Metric table SHALL store metric type, value, unit, and timestamp
3. THE Health_Metric table SHALL store the source Health_Platform for each metric
4. THE Health_Metric table SHALL associate each record with a User_Account
5. THE Supabase_Database SHALL enforce unique constraints to prevent duplicate Health_Metrics
6. THE Supabase_Database SHALL create indexes for efficient metric queries by user, type, and date range
7. THE Health_Metric table SHALL support storage of steps, heart rate, sleep duration, and exercise data
8. THE Supabase_Database SHALL optimize storage for high-volume time-series health data

### Requirement 106: Supabase Storage for Food Photos

**User Story:** As a user, I want my food photos stored securely, so that I can view them with my meal logs

#### Acceptance Criteria

1. THE Supabase_Storage SHALL contain a Storage_Bucket for Food_Photos
2. WHEN a user captures a food photo, THE Image_Logger SHALL upload the photo to Supabase_Storage
3. THE Supabase_Storage SHALL generate a unique identifier for each Food_Photo
4. THE Supabase_Storage SHALL return a secure URL for accessing the Food_Photo
5. THE Meal_Log SHALL store the Supabase_Storage URL reference for associated Food_Photos
6. THE Supabase_Storage SHALL enforce file size limits for Food_Photos
7. THE Supabase_Storage SHALL support image format validation
8. WHEN a Meal_Log is deleted, THE Health_App SHALL delete the associated Food_Photo from Supabase_Storage

### Requirement 107: Supabase Row-Level Security for Data Privacy

**User Story:** As a user, I want my data protected so that other users cannot access my health information

#### Acceptance Criteria

1. THE Supabase_Database SHALL implement Row_Level_Security on all user data tables
2. THE Supabase_Database SHALL create RLS_Policies that restrict data access to the authenticated user
3. THE RLS_Policy for Meal_Logs SHALL ensure users can only access their own meal records
4. THE RLS_Policy for Water_Logs SHALL ensure users can only access their own water records
5. THE RLS_Policy for Fasting_Sessions SHALL ensure users can only access their own fasting records
6. THE RLS_Policy for Health_Metrics SHALL ensure users can only access their own synced health data
7. THE RLS_Policy for Subscription_Records SHALL ensure users can only access their own subscription data
8. THE Supabase_Storage SHALL implement security policies ensuring users can only access their own Food_Photos
9. THE Supabase_Database SHALL enforce RLS_Policies at the database level for all queries

### Requirement 108: Supabase Realtime for Live Updates

**User Story:** As a user, I want my data to sync in real-time across devices, so that my health tracking is always current

#### Acceptance Criteria

1. THE Health_App SHALL subscribe to Supabase_Realtime channels for user data updates
2. WHEN a Meal_Log is created on one device, THE Supabase_Realtime SHALL notify other devices
3. WHEN a Water_Log is created on one device, THE Supabase_Realtime SHALL notify other devices
4. WHEN a Fasting_Session is updated on one device, THE Supabase_Realtime SHALL notify other devices
5. WHEN a Subscription_Tier changes, THE Supabase_Realtime SHALL notify all user devices immediately
6. THE Health_App SHALL update the user interface automatically when receiving Realtime_Channel updates
7. THE Supabase_Realtime SHALL only send updates for data belonging to the authenticated user
8. WHEN network connectivity is lost, THE Health_App SHALL queue local changes and sync when reconnected

### Requirement 109: Amazon Bedrock Integration

**User Story:** As a developer, I want to use Amazon Bedrock with Claude AI for all AI features, so that the app has powerful natural language capabilities

#### Acceptance Criteria

1. THE Health_App SHALL integrate with Amazon_Bedrock for accessing Claude_AI
2. THE Health_App SHALL use Bedrock_Client to communicate with Amazon_Bedrock API
3. THE Health_App SHALL store Amazon_Bedrock credentials securely
4. THE Health_App SHALL transmit all Amazon_Bedrock API requests over secure connections
5. THE Bedrock_Client SHALL handle API authentication and request signing
6. THE Bedrock_Client SHALL implement retry logic with exponential backoff for failed requests
7. THE Bedrock_Client SHALL respect Amazon_Bedrock rate limits and quotas
8. WHEN Amazon_Bedrock is unavailable, THE Health_App SHALL display appropriate error messages

### Requirement 110: Claude AI for Image-Based Meal Logging

**User Story:** As a user, I want Claude AI to analyze my food photos, so that I get accurate meal identification

#### Acceptance Criteria

1. WHEN a user captures a food photo, THE AI_Image_Analyzer SHALL send the image to Claude_AI through Amazon_Bedrock
2. THE AI_Image_Analyzer SHALL request Claude_AI to identify food items, portion sizes, and ingredients
3. WHEN Claude_AI returns analysis results, THE AI_Image_Analyzer SHALL parse the food items and quantities
4. THE AI_Image_Analyzer SHALL request Claude_AI to estimate nutritional information for identified foods
5. THE Image_Logger SHALL create a Meal_Log with the Claude_AI analysis results
6. THE AI_Image_Analyzer SHALL handle cases where Claude_AI cannot identify foods with confidence
7. THE AI_Image_Analyzer SHALL provide confidence scores for food identifications
8. THE Health_App SHALL allow users to correct Claude_AI identifications

### Requirement 111: Claude AI for Text-Based Meal Logging

**User Story:** As a user, I want Claude AI to understand my meal descriptions, so that I can log meals naturally

#### Acceptance Criteria

1. WHEN a user submits text describing a meal, THE AI_Text_Processor SHALL send the text to Claude_AI through Amazon_Bedrock
2. THE AI_Text_Processor SHALL request Claude_AI to parse food items, quantities, and preparation methods
3. WHEN Claude_AI returns parsed results, THE AI_Text_Processor SHALL extract structured meal data
4. THE AI_Text_Processor SHALL request Claude_AI to estimate nutritional information for parsed foods
5. THE Text_Logger SHALL create a Meal_Log with the Claude_AI analysis results
6. THE AI_Text_Processor SHALL handle ambiguous descriptions by requesting clarification from the user
7. THE AI_Text_Processor SHALL support natural language including colloquial food names and measurements
8. THE AI_Text_Processor SHALL recognize meal context such as "breakfast", "snack", or "dinner"

### Requirement 112: Claude AI for Voice-Based Meal Logging

**User Story:** As a user, I want Claude AI to transcribe and understand my voice input, so that I can log meals hands-free

#### Acceptance Criteria

1. WHEN a user provides voice input, THE AI_Speech_Transcriber SHALL send the audio to Claude_AI through Amazon_Bedrock
2. THE AI_Speech_Transcriber SHALL request Claude_AI to transcribe the audio to text
3. WHEN Claude_AI returns transcription, THE AI_Speech_Transcriber SHALL pass the text to AI_Text_Processor
4. THE AI_Text_Processor SHALL parse the transcribed text to extract meal information
5. THE Voice_Logger SHALL create a Meal_Log with the parsed meal data
6. THE AI_Speech_Transcriber SHALL handle background noise and unclear audio gracefully
7. THE AI_Speech_Transcriber SHALL support multiple languages based on Language_Settings
8. THE AI_Speech_Transcriber SHALL provide transcription confidence scores

### Requirement 113: Claude AI for Amigo Conversations

**User Story:** As a Pro subscriber, I want Claude AI to power Amigo conversations, so that I receive intelligent and helpful coaching

#### Acceptance Criteria

1. WHERE Pro tier is active, WHEN a user sends a message to Amigo, THE AI_Conversation_Engine SHALL send the message to Claude_AI
2. THE AI_Conversation_Engine SHALL provide Claude_AI with relevant user context including recent meals and health data
3. THE AI_Conversation_Engine SHALL instruct Claude_AI to respond as Amigo with a friendly, supportive coaching tone
4. WHEN Claude_AI generates a response, THE AI_Conversation_Engine SHALL display it in the Chat_Interface
5. THE AI_Conversation_Engine SHALL maintain conversation flow and context across multiple messages
6. THE AI_Conversation_Engine SHALL ensure Claude_AI responses are health-focused and appropriate
7. THE AI_Conversation_Engine SHALL handle cases where Claude_AI cannot answer a question
8. THE AI_Conversation_Engine SHALL limit response length to maintain conversational pacing

### Requirement 114: Claude AI for Health Insights Generation

**User Story:** As a Pro subscriber, I want Claude AI to generate personalized health insights, so that I understand my health patterns

#### Acceptance Criteria

1. WHERE Pro tier is active, THE AI_Insight_Generator SHALL use Claude_AI to analyze user health data
2. THE AI_Insight_Generator SHALL provide Claude_AI with meal history, water intake, fasting patterns, and Health_Metrics
3. THE AI_Insight_Generator SHALL request Claude_AI to identify trends, patterns, and correlations
4. WHEN Claude_AI generates insights, THE Health_App SHALL display them on the Dashboard
5. THE AI_Insight_Generator SHALL generate daily, weekly, and monthly insights
6. THE AI_Insight_Generator SHALL ensure insights are actionable and health-focused
7. THE AI_Insight_Generator SHALL highlight positive progress and areas for improvement
8. THE AI_Insight_Generator SHALL update insights when new health data is logged

### Requirement 115: User AI Context Storage

**User Story:** As a user, I want Amigo to remember my preferences and history, so that coaching feels personalized and continuous

#### Acceptance Criteria

1. THE Supabase_Database SHALL contain a Database_Table for User_AI_Context
2. THE User_AI_Context table SHALL store personalized AI data for each user
3. THE User_AI_Context table SHALL associate each record with a User_Account
4. THE AI_Memory_Store SHALL persist User_AI_Context to Supabase_Database after each AI interaction
5. THE AI_Memory_Store SHALL implement Row_Level_Security for User_AI_Context data
6. THE User_AI_Context SHALL be encrypted at rest in Supabase_Database
7. WHEN a user deletes their account, THE Health_App SHALL delete all User_AI_Context data
8. THE AI_Memory_Store SHALL optimize storage for efficient context retrieval

### Requirement 116: Conversation History Tracking

**User Story:** As a Pro subscriber, I want Amigo to remember our past conversations, so that I don't have to repeat myself

#### Acceptance Criteria

1. THE Supabase_Database SHALL contain a Database_Table for Conversation_History
2. THE Conversation_History table SHALL store user messages and Amigo responses with timestamps
3. THE Conversation_History table SHALL associate each conversation with a User_Account
4. WHEN a user sends a message to Amigo, THE AI_Memory_Store SHALL store the message in Conversation_History
5. WHEN Amigo responds, THE AI_Memory_Store SHALL store the response in Conversation_History
6. THE AI_Conversation_Engine SHALL retrieve recent Conversation_History when generating responses
7. THE Conversation_History SHALL be limited to the most recent conversations to manage Context_Window size
8. THE AI_Memory_Store SHALL implement Row_Level_Security for Conversation_History data

### Requirement 117: User Pattern Profile Learning

**User Story:** As a user, I want Amigo to learn my eating patterns and habits, so that coaching becomes more relevant over time

#### Acceptance Criteria

1. THE Pattern_Analyzer SHALL analyze Meal_Logs to identify eating patterns
2. THE Pattern_Analyzer SHALL identify typical meal times for breakfast, lunch, and dinner
3. THE Pattern_Analyzer SHALL identify frequently consumed foods and food categories
4. THE Pattern_Analyzer SHALL identify portion size preferences and eating frequency
5. THE Pattern_Analyzer SHALL store identified patterns in User_Pattern_Profile
6. THE User_Pattern_Profile SHALL be stored in the User_AI_Context table
7. THE Pattern_Analyzer SHALL update User_Pattern_Profile as new meal data is logged
8. THE AI_Conversation_Engine SHALL reference User_Pattern_Profile when generating coaching responses

### Requirement 118: Fasting Habit Analysis

**User Story:** As a user who practices fasting, I want Amigo to understand my fasting habits, so that coaching supports my fasting goals

#### Acceptance Criteria

1. THE Pattern_Analyzer SHALL analyze Fasting_Sessions to identify fasting patterns
2. THE Pattern_Analyzer SHALL identify preferred Fasting_Protocols and typical fasting durations
3. THE Pattern_Analyzer SHALL calculate fasting success rates and completion patterns
4. THE Pattern_Analyzer SHALL identify times of day when fasting is most challenging
5. THE Pattern_Analyzer SHALL identify correlations between fasting and meal timing
6. THE Pattern_Analyzer SHALL store fasting patterns in User_Pattern_Profile
7. THE AI_Conversation_Engine SHALL reference fasting patterns when providing coaching
8. THE AI_Conversation_Engine SHALL suggest protocol adjustments based on success patterns

### Requirement 119: Water Intake Pattern Recognition

**User Story:** As a user, I want Amigo to understand my hydration habits, so that reminders and coaching are optimally timed

#### Acceptance Criteria

1. THE Pattern_Analyzer SHALL analyze Water_Logs to identify hydration patterns
2. THE Pattern_Analyzer SHALL identify typical times of day when the user drinks water
3. THE Pattern_Analyzer SHALL calculate average daily water intake and consistency
4. THE Pattern_Analyzer SHALL identify days when Water_Goals are typically met or missed
5. THE Pattern_Analyzer SHALL store hydration patterns in User_Pattern_Profile
6. THE Notification_Scheduler SHALL use hydration patterns to optimize Water_Reminder timing
7. THE AI_Conversation_Engine SHALL reference hydration patterns when providing coaching
8. THE Pattern_Analyzer SHALL identify correlations between water intake and meal timing

### Requirement 120: Preference Tracking and Learning

**User Story:** As a user, I want Amigo to remember my preferences, so that interactions feel natural and personalized

#### Acceptance Criteria

1. THE Preference_Tracker SHALL learn user preferences from interactions and explicit feedback
2. THE Preference_Tracker SHALL track preferred meal logging methods
3. THE Preference_Tracker SHALL track food preferences and dietary restrictions mentioned in conversations
4. THE Preference_Tracker SHALL track health goals and priorities expressed by the user
5. THE Preference_Tracker SHALL track preferred coaching style and communication preferences
6. THE Preference_Tracker SHALL store learned preferences in User_AI_Context
7. THE AI_Conversation_Engine SHALL reference preferences when generating responses
8. THE Preference_Tracker SHALL update preferences as user behavior and feedback evolve

### Requirement 121: Session Context Loading

**User Story:** As a Pro subscriber, I want Amigo to have relevant context when I start a conversation, so that responses are immediately personalized

#### Acceptance Criteria

1. WHEN a user opens the Chat_Interface, THE AI_Conversation_Engine SHALL load Session_Context
2. THE Session_Context SHALL include recent Conversation_History
3. THE Session_Context SHALL include User_Pattern_Profile with eating, fasting, and hydration patterns
4. THE Session_Context SHALL include recent Meal_Logs, Water_Logs, and Fasting_Sessions
5. THE Session_Context SHALL include current health goals and progress toward goals
6. THE Session_Context SHALL include learned preferences from Preference_Tracker
7. THE AI_Conversation_Engine SHALL provide Session_Context to Claude_AI with each message
8. THE Session_Context SHALL be optimized to fit within Claude_AI Context_Window limits

### Requirement 122: Coaching Style Adaptation

**User Story:** As a user, I want Amigo's coaching style to adapt to my progress, so that support matches my current needs

#### Acceptance Criteria

1. THE Coaching_Adaptation_Engine SHALL analyze user progress toward health goals
2. WHEN a user consistently meets goals, THE Coaching_Adaptation_Engine SHALL adjust to a maintenance coaching style
3. WHEN a user struggles with goals, THE Coaching_Adaptation_Engine SHALL adjust to a more supportive coaching style
4. WHEN a user shows improvement, THE Coaching_Adaptation_Engine SHALL provide positive reinforcement
5. THE Coaching_Adaptation_Engine SHALL store coaching style preferences in User_AI_Context
6. THE AI_Conversation_Engine SHALL instruct Claude_AI to use the adapted coaching style
7. THE Coaching_Adaptation_Engine SHALL adjust coaching frequency based on user engagement
8. THE Coaching_Adaptation_Engine SHALL identify when users need encouragement versus challenge

### Requirement 123: Continuity Across Sessions

**User Story:** As a user, I want Amigo to feel like the same AI companion each time I interact, so that the relationship feels consistent

#### Acceptance Criteria

1. THE AI_Conversation_Engine SHALL reference past conversations when generating responses
2. WHEN a user mentions a previous topic, THE AI_Conversation_Engine SHALL retrieve relevant Conversation_History
3. THE AI_Conversation_Engine SHALL maintain awareness of ongoing health challenges discussed in previous sessions
4. THE AI_Conversation_Engine SHALL follow up on goals and commitments from previous conversations
5. THE AI_Conversation_Engine SHALL avoid asking for information already provided in past conversations
6. THE AI_Conversation_Engine SHALL acknowledge user progress since the last conversation
7. THE AI_Conversation_Engine SHALL maintain consistent personality and tone across all sessions
8. THE AI_Conversation_Engine SHALL reference User_Pattern_Profile to demonstrate long-term understanding

### Requirement 124: Personalized Insight Generation

**User Story:** As a Pro subscriber, I want insights tailored to my specific patterns, so that recommendations are relevant to me

#### Acceptance Criteria

1. THE AI_Insight_Generator SHALL use User_Pattern_Profile when generating insights
2. THE AI_Insight_Generator SHALL compare current behavior to historical patterns
3. THE AI_Insight_Generator SHALL identify deviations from typical patterns
4. THE AI_Insight_Generator SHALL correlate patterns across meal timing, fasting, and hydration
5. THE AI_Insight_Generator SHALL generate insights specific to the user's health goals
6. THE AI_Insight_Generator SHALL avoid generic advice in favor of personalized recommendations
7. THE AI_Insight_Generator SHALL reference specific meals and behaviors from user history
8. THE AI_Insight_Generator SHALL track which insights lead to behavior changes

### Requirement 125: AI Context Evolution Over Time

**User Story:** As a long-term user, I want Amigo to become more knowledgeable about me over time, so that coaching improves continuously

#### Acceptance Criteria

1. THE AI_Memory_Store SHALL accumulate User_AI_Context data over the lifetime of the account
2. THE Pattern_Analyzer SHALL identify long-term trends spanning weeks and months
3. THE Pattern_Analyzer SHALL identify seasonal patterns in eating and health behaviors
4. THE Pattern_Analyzer SHALL track how user patterns change in response to coaching
5. THE AI_Conversation_Engine SHALL reference long-term patterns when providing coaching
6. THE AI_Memory_Store SHALL prioritize recent context while maintaining historical awareness
7. THE Pattern_Analyzer SHALL identify which coaching approaches are most effective for the user
8. THE User_AI_Context SHALL grow more comprehensive as the user engages with the app

### Requirement 126: Context Window Management

**User Story:** As a developer, I want to manage Claude AI context efficiently, so that API costs are optimized while maintaining personalization

#### Acceptance Criteria

1. THE AI_Conversation_Engine SHALL limit Session_Context to fit within Claude_AI Context_Window constraints
2. THE AI_Conversation_Engine SHALL prioritize recent and relevant context over older data
3. THE AI_Conversation_Engine SHALL summarize older Conversation_History to preserve key information
4. THE AI_Conversation_Engine SHALL include the most relevant User_Pattern_Profile data for each interaction
5. THE AI_Conversation_Engine SHALL dynamically adjust context based on conversation topic
6. THE AI_Conversation_Engine SHALL track Context_Window usage to prevent exceeding limits
7. THE AI_Conversation_Engine SHALL implement context compression strategies for long-term users
8. THE AI_Conversation_Engine SHALL balance context richness with API performance and cost

### Requirement 127: AI Context Privacy and Security

**User Story:** As a user, I want my AI context data protected, so that my personal patterns and conversations remain private

#### Acceptance Criteria

1. THE AI_Memory_Store SHALL encrypt User_AI_Context at rest in Supabase_Database
2. THE AI_Memory_Store SHALL encrypt Conversation_History at rest in Supabase_Database
3. THE AI_Memory_Store SHALL transmit all AI context data over secure connections
4. THE Supabase_Database SHALL enforce Row_Level_Security for User_AI_Context and Conversation_History
5. THE Health_App SHALL not share User_AI_Context with third parties
6. WHEN a user deletes their account, THE Health_App SHALL permanently delete all User_AI_Context and Conversation_History
7. THE AI_Conversation_Engine SHALL not include sensitive personal information in Claude_AI requests beyond health data
8. THE Health_App SHALL comply with data privacy regulations for AI context storage and processing

### Requirement 128: Amigo Personality Consistency

**User Story:** As a user, I want Amigo to have a consistent personality, so that interactions feel like talking to the same friend

#### Acceptance Criteria

1. THE AI_Conversation_Engine SHALL provide Claude_AI with consistent personality instructions for Amigo
2. THE Amigo personality SHALL be friendly, supportive, and non-judgmental
3. THE Amigo personality SHALL use encouraging language and positive reinforcement
4. THE Amigo personality SHALL avoid medical advice and focus on lifestyle coaching
5. THE Amigo personality SHALL be conversational and approachable rather than clinical
6. THE AI_Conversation_Engine SHALL ensure Amigo references past interactions to maintain relationship continuity
7. THE Amigo personality SHALL adapt tone based on user emotional state and progress
8. THE AI_Conversation_Engine SHALL maintain Amigo personality consistency across all AI-powered features

### Requirement 129: Proactive Coaching Based on Patterns

**User Story:** As a Pro subscriber, I want Amigo to proactively offer coaching based on my patterns, so that I receive timely support

#### Acceptance Criteria

1. WHERE Pro tier is active, THE AI_Coach SHALL analyze User_Pattern_Profile to identify coaching opportunities
2. WHEN the Pattern_Analyzer detects a deviation from healthy patterns, THE AI_Coach SHALL generate a proactive coaching message
3. WHEN a user approaches a typical challenge time, THE AI_Coach SHALL offer preemptive support
4. WHEN a user achieves a milestone, THE AI_Coach SHALL provide recognition and encouragement
5. THE AI_Coach SHALL send proactive coaching through Push_Notifications with user permission
6. THE AI_Coach SHALL limit proactive coaching frequency to avoid overwhelming the user
7. THE AI_Coach SHALL personalize proactive messages based on User_AI_Context
8. THE AI_Coach SHALL track effectiveness of proactive coaching and adjust approach

### Requirement 130: Multi-Device AI Context Synchronization

**User Story:** As a user with multiple devices, I want Amigo to have the same context on all devices, so that conversations are seamless

#### Acceptance Criteria

1. THE AI_Memory_Store SHALL store User_AI_Context in Supabase_Database for cross-device access
2. WHEN a user interacts with Amigo on one device, THE AI_Memory_Store SHALL update User_AI_Context in Supabase_Database
3. WHEN a user opens the Chat_Interface on another device, THE AI_Conversation_Engine SHALL load the latest User_AI_Context
4. THE Supabase_Realtime SHALL notify devices when User_AI_Context is updated
5. THE AI_Conversation_Engine SHALL maintain conversation continuity across device switches
6. THE Conversation_History SHALL be accessible from all user devices
7. THE User_Pattern_Profile SHALL be synchronized across all devices
8. THE Health_App SHALL handle concurrent AI interactions from multiple devices gracefully

### Requirement 131: AI Feature Performance Optimization

**User Story:** As a user, I want AI features to respond quickly, so that meal logging and conversations feel smooth

#### Acceptance Criteria

1. THE AI_Image_Analyzer SHALL return food identification results within 5 seconds for typical images
2. THE AI_Text_Processor SHALL parse meal descriptions within 2 seconds for typical input
3. THE AI_Speech_Transcriber SHALL transcribe voice input within 3 seconds for typical audio
4. THE AI_Conversation_Engine SHALL generate Amigo responses within 3 seconds for typical messages
5. THE Bedrock_Client SHALL implement request caching for repeated queries
6. THE AI_Conversation_Engine SHALL implement streaming responses for longer Claude_AI outputs
7. THE Health_App SHALL display loading indicators during AI processing
8. THE Health_App SHALL allow users to cancel long-running AI requests

### Requirement 132: AI Error Handling and Fallbacks

**User Story:** As a user, I want the app to handle AI failures gracefully, so that I can continue using features when AI services are unavailable

#### Acceptance Criteria

1. IF Claude_AI is unavailable, THEN THE Health_App SHALL allow manual meal entry without AI assistance
2. IF the AI_Image_Analyzer fails, THEN THE Health_App SHALL allow users to manually enter food items
3. IF the AI_Text_Processor fails, THEN THE Health_App SHALL allow structured manual food entry
4. IF the AI_Speech_Transcriber fails, THEN THE Health_App SHALL offer text or image input as alternatives
5. IF the AI_Conversation_Engine fails, THEN THE Health_App SHALL display a service unavailable message
6. THE Health_App SHALL cache recent AI responses for offline reference
7. THE Health_App SHALL log AI errors for monitoring and troubleshooting
8. WHEN AI services recover, THE Health_App SHALL automatically resume AI-powered features

### Requirement 133: Supabase Database Backup and Recovery

**User Story:** As a user, I want my data backed up, so that I don't lose my health tracking history

#### Acceptance Criteria

1. THE Supabase_Database SHALL implement automated backup procedures
2. THE Supabase_Database SHALL retain backups for at least 30 days
3. THE Supabase_Database SHALL support point-in-time recovery for data restoration
4. THE Health_App SHALL provide mechanisms to restore user data from backups if needed
5. THE Supabase_Database SHALL test backup restoration procedures regularly
6. THE Supabase_Database SHALL encrypt backups at rest
7. THE Supabase_Database SHALL store backups in geographically distributed locations
8. THE Health_App SHALL notify users if data restoration is performed on their account

### Requirement 134: Supabase Performance Monitoring

**User Story:** As a developer, I want to monitor Supabase performance, so that I can ensure the app remains responsive

#### Acceptance Criteria

1. THE Health_App SHALL monitor Supabase_Database query performance
2. THE Health_App SHALL track Supabase_Storage upload and download speeds
3. THE Health_App SHALL monitor Supabase_Realtime connection stability
4. THE Health_App SHALL track Supabase_Auth authentication success rates
5. THE Health_App SHALL log slow queries and performance bottlenecks
6. THE Health_App SHALL implement database query optimization based on performance metrics
7. THE Health_App SHALL alert developers when Supabase performance degrades
8. THE Health_App SHALL provide performance dashboards for monitoring Supabase operations

### Requirement 135: Amazon Bedrock Cost Management

**User Story:** As a developer, I want to manage Amazon Bedrock costs, so that AI features remain economically sustainable

#### Acceptance Criteria

1. THE Bedrock_Client SHALL track Claude_AI API usage including tokens and requests
2. THE Health_App SHALL implement usage quotas for AI features based on Subscription_Tier
3. WHERE Free tier is active, THE Health_App SHALL limit AI-powered meal logging to tier quotas
4. WHERE Pro tier is active, THE Health_App SHALL provide unlimited AI usage
5. THE Bedrock_Client SHALL optimize prompts to minimize token usage
6. THE Health_App SHALL cache AI responses to reduce redundant API calls
7. THE Health_App SHALL monitor AI costs and alert when usage exceeds budgets

### Requirement 136: AI Context Summarization

**User Story:** As a long-term user, I want my extensive history summarized efficiently, so that Amigo remains knowledgeable without performance issues

#### Acceptance Criteria

1. WHEN User_AI_Context exceeds size thresholds, THE Pattern_Analyzer SHALL generate summaries
2. THE Pattern_Analyzer SHALL use Claude_AI to summarize older Conversation_History
3. THE Pattern_Analyzer SHALL preserve key insights and patterns in summaries
4. THE Pattern_Analyzer SHALL replace detailed historical data with summaries to manage storage
5. THE AI_Conversation_Engine SHALL use summaries when detailed history is not needed
6. THE Pattern_Analyzer SHALL maintain detailed recent history while summarizing older data
7. THE AI_Memory_Store SHALL store both detailed and summarized context appropriately
8. THE Pattern_Analyzer SHALL update summaries periodically as new patterns emerge

### Requirement 137: Amigo Learning from User Feedback

**User Story:** As a user, I want to provide feedback on Amigo's responses, so that coaching improves over time

#### Acceptance Criteria

1. THE Chat_Interface SHALL allow users to rate Amigo responses as helpful or not helpful
2. WHEN a user provides feedback, THE AI_Memory_Store SHALL store the feedback with the conversation
3. THE Coaching_Adaptation_Engine SHALL analyze feedback patterns to adjust coaching approach
4. THE Coaching_Adaptation_Engine SHALL identify which types of responses receive positive feedback
5. THE AI_Conversation_Engine SHALL use feedback history to improve future responses
6. THE Preference_Tracker SHALL learn communication preferences from feedback
7. THE Health_App SHALL allow users to provide detailed feedback on specific responses
8. THE Coaching_Adaptation_Engine SHALL prioritize coaching styles that receive consistent positive feedback

### Requirement 138: Health Goal Selection and Management

**User Story:** As a user, I want to set and switch between different health goals, so that Amigo's coaching aligns with my current priorities

#### Acceptance Criteria

1. THE Health_App SHALL allow users to select a Health_Goal from predefined options
2. THE Health_Goal options SHALL include weight loss, muscle gain, maintenance, improved energy, and better sleep
3. WHEN a user selects a Health_Goal, THE Health_App SHALL set it as the Active_Goal
4. THE Health_App SHALL store the Active_Goal with a start date in the User_Account
5. THE Health_App SHALL allow users to switch to a different Health_Goal at any time
6. WHEN a user switches Health_Goals, THE Health_App SHALL create a Goal_Transition record
7. THE Health_App SHALL display the current Active_Goal on the Dashboard
8. THE Health_App SHALL allow users to view their Goal_History

### Requirement 139: Goal History Tracking

**User Story:** As a user, I want my previous health goals tracked, so that I can see my health journey over time

#### Acceptance Criteria

1. THE Supabase_Database SHALL contain a Database_Table for Goal_History
2. WHEN a user switches from one Health_Goal to another, THE Health_App SHALL create a Goal_Summary for the previous goal
3. THE Goal_Summary SHALL include the goal type, start date, end date, and duration
4. THE Goal_Summary SHALL include a brief summary of outcomes and progress during that goal period
5. THE Goal_History table SHALL associate each record with a User_Account
6. THE Goal_History table SHALL store the reason for Goal_Transition when provided by the user
7. THE Health_App SHALL display Goal_History in chronological order
8. THE Supabase_Database SHALL implement Row_Level_Security for Goal_History data

### Requirement 140: Goal-Specific AI Context Storage

**User Story:** As a user, I want Amigo to remember patterns specific to each goal, so that coaching is relevant when I return to a previous goal

#### Acceptance Criteria

1. THE User_AI_Context table SHALL store Goal_Context data associated with each Health_Goal
2. THE Goal_Context SHALL include goal-specific patterns identified by the Pattern_Analyzer
3. THE Goal_Context SHALL include goal-specific progress metrics and achievements
4. THE Goal_Context SHALL include coaching adaptations specific to that Health_Goal
5. WHEN a user switches Health_Goals, THE AI_Memory_Store SHALL preserve the Goal_Context for the previous goal
6. WHEN a user returns to a previous Health_Goal, THE AI_Conversation_Engine SHALL load the associated Goal_Context
7. THE Goal_Context SHALL be stored separately from general User_AI_Context to maintain goal-specific learning
8. THE AI_Memory_Store SHALL optimize storage for multiple Goal_Context records per user

### Requirement 141: Active Goal Prioritization in AI Context

**User Story:** As a user, I want Amigo to focus on my current goal, so that coaching is immediately relevant to what I'm working on now

#### Acceptance Criteria

1. WHEN loading Session_Context, THE AI_Conversation_Engine SHALL prioritize the Active_Goal and its Goal_Context
2. THE Session_Context SHALL include the Active_Goal type and start date
3. THE Session_Context SHALL include goal-specific patterns from the current Goal_Context
4. THE Session_Context SHALL include progress metrics relevant to the Active_Goal
5. THE AI_Conversation_Engine SHALL instruct Claude_AI to focus coaching on the Active_Goal
6. THE AI_Conversation_Engine SHALL provide Claude_AI with the Active_Goal as primary context
7. THE Session_Context SHALL include relevant Goal_History when it provides context for the Active_Goal
8. THE AI_Conversation_Engine SHALL balance Active_Goal focus with overall health awareness

### Requirement 142: Goal-Specific Pattern Analysis

**User Story:** As a user, I want Amigo to identify patterns specific to my current goal, so that insights are targeted and actionable

#### Acceptance Criteria

1. THE Pattern_Analyzer SHALL analyze meal patterns in the context of the Active_Goal
2. THE Pattern_Analyzer SHALL identify which foods and meal timings correlate with Active_Goal progress
3. THE Pattern_Analyzer SHALL analyze fasting patterns specific to the Active_Goal
4. THE Pattern_Analyzer SHALL analyze water intake patterns in relation to the Active_Goal
5. THE Pattern_Analyzer SHALL store goal-specific patterns in the current Goal_Context
6. THE Pattern_Analyzer SHALL compare current patterns to patterns from previous instances of the same Health_Goal
7. THE Pattern_Analyzer SHALL identify which behaviors are most effective for each Health_Goal type
8. THE AI_Insight_Generator SHALL generate insights focused on Active_Goal progress

### Requirement 143: Goal-Based Meal Recommendations

**User Story:** As a user, I want meal recommendations aligned with my current goal, so that food suggestions support my objectives

#### Acceptance Criteria

1. WHERE Pro tier is active, THE AI_Coach SHALL provide meal recommendations based on the Active_Goal
2. WHEN the Active_Goal is weight loss, THE AI_Coach SHALL recommend meals with appropriate caloric deficit
3. WHEN the Active_Goal is muscle gain, THE AI_Coach SHALL recommend meals with higher protein content
4. WHEN the Active_Goal is maintenance, THE AI_Coach SHALL recommend balanced meals for stable weight
5. WHEN the Active_Goal is improved energy, THE AI_Coach SHALL recommend meals that support sustained energy levels
6. WHEN the Active_Goal is better sleep, THE AI_Coach SHALL recommend meals and timing that support sleep quality
7. THE AI_Coach SHALL reference User_Pattern_Profile to personalize recommendations within goal parameters
8. THE AI_Coach SHALL adjust recommendations based on goal-specific progress patterns

### Requirement 144: Goal-Based Fasting Protocol Suggestions

**User Story:** As a user who practices fasting, I want fasting suggestions aligned with my current goal, so that my fasting protocol supports my objectives

#### Acceptance Criteria

1. WHERE Pro tier is active, THE AI_Coach SHALL suggest Fasting_Protocols based on the Active_Goal
2. WHEN the Active_Goal is weight loss, THE AI_Coach SHALL suggest fasting protocols that support caloric deficit
3. WHEN the Active_Goal is muscle gain, THE AI_Coach SHALL suggest fasting protocols that preserve muscle mass
4. WHEN the Active_Goal is improved energy, THE AI_Coach SHALL suggest fasting protocols that enhance metabolic flexibility
5. WHEN the Active_Goal is better sleep, THE AI_Coach SHALL suggest fasting protocols that don't interfere with sleep quality
6. THE AI_Coach SHALL consider User_Pattern_Profile fasting habits when suggesting protocols
7. THE AI_Coach SHALL adjust fasting suggestions based on goal-specific success patterns
8. THE AI_Coach SHALL reference Goal_History to suggest protocols that worked well for previous instances of the same goal

### Requirement 145: Goal-Based Water Intake Targets

**User Story:** As a user, I want water intake recommendations adjusted for my current goal, so that hydration supports my objectives

#### Acceptance Criteria

1. THE Health_App SHALL adjust Water_Goal recommendations based on the Active_Goal
2. WHEN the Active_Goal is weight loss, THE Health_App SHALL suggest water intake that supports metabolism
3. WHEN the Active_Goal is muscle gain, THE Health_App SHALL suggest increased water intake for muscle recovery
4. WHEN the Active_Goal is improved energy, THE Health_App SHALL suggest water intake that prevents fatigue
5. WHEN the Active_Goal is better sleep, THE Health_App SHALL suggest water intake timing that doesn't disrupt sleep
6. THE AI_Coach SHALL provide goal-specific hydration coaching and reminders
7. THE Pattern_Analyzer SHALL track water intake patterns specific to each Health_Goal
8. THE AI_Coach SHALL adjust water recommendations based on goal-specific progress

### Requirement 146: Goal-Focused Insights and Analysis

**User Story:** As a Pro subscriber, I want insights focused on my current goal, so that I understand my progress toward what matters most

#### Acceptance Criteria

1. WHERE Pro tier is active, THE AI_Insight_Generator SHALL generate insights focused on the Active_Goal
2. THE AI_Insight_Generator SHALL analyze progress metrics relevant to the Active_Goal
3. THE AI_Insight_Generator SHALL identify behaviors that support or hinder Active_Goal progress
4. THE AI_Insight_Generator SHALL compare current progress to typical progress patterns for the Health_Goal type
5. THE AI_Insight_Generator SHALL reference Goal_History to show progress compared to previous attempts at the same goal
6. THE Dashboard SHALL display goal-specific Quick_Stats and progress indicators
7. THE AI_Insight_Generator SHALL highlight milestones and achievements related to the Active_Goal
8. THE AI_Insight_Generator SHALL provide actionable recommendations for improving Active_Goal progress

### Requirement 147: Goal Transition Context Management

**User Story:** As a user who switches goals, I want Amigo to understand why I'm changing focus, so that coaching adapts appropriately

#### Acceptance Criteria

1. WHEN a user initiates a Goal_Transition, THE Health_App SHALL optionally prompt for a transition reason
2. THE Goal_Transition record SHALL store the reason for switching Health_Goals
3. THE AI_Memory_Store SHALL create a Goal_Summary for the previous Active_Goal during transition
4. THE Goal_Summary SHALL include AI-generated summary of progress and outcomes during that goal period
5. THE AI_Conversation_Engine SHALL acknowledge the Goal_Transition in the next coaching interaction
6. THE AI_Conversation_Engine SHALL reference the transition reason when providing coaching for the new Active_Goal
7. THE Pattern_Analyzer SHALL analyze patterns across Goal_Transitions to identify optimal goal sequencing
8. THE AI_Coach SHALL provide encouragement and context-appropriate support during Goal_Transitions

### Requirement 148: Cross-Goal Pattern Recognition

**User Story:** As a user with multiple goals over time, I want Amigo to recognize patterns that apply across different goals, so that I benefit from cumulative learning

#### Acceptance Criteria

1. THE Pattern_Analyzer SHALL identify patterns that remain consistent across different Health_Goals
2. THE Pattern_Analyzer SHALL identify which behaviors support multiple Health_Goal types
3. THE Pattern_Analyzer SHALL identify which Health_Goal sequences are most effective for the user
4. THE Pattern_Analyzer SHALL store cross-goal patterns in the general User_Pattern_Profile
5. THE AI_Conversation_Engine SHALL reference cross-goal patterns when providing coaching
6. THE AI_Insight_Generator SHALL generate insights about behaviors that support overall health regardless of Active_Goal
7. THE Pattern_Analyzer SHALL identify optimal timing for Goal_Transitions based on progress patterns
8. THE AI_Coach SHALL help users understand how different goals relate to their overall health journey

### Requirement 149: Goal-Based Coaching Style Adaptation

**User Story:** As a user, I want Amigo's coaching style to match my current goal, so that support feels appropriate for what I'm trying to achieve

#### Acceptance Criteria

1. THE Coaching_Adaptation_Engine SHALL adjust coaching style based on the Active_Goal
2. WHEN the Active_Goal is weight loss, THE Coaching_Adaptation_Engine SHALL emphasize consistency and sustainable habits
3. WHEN the Active_Goal is muscle gain, THE Coaching_Adaptation_Engine SHALL emphasize progressive challenge and recovery
4. WHEN the Active_Goal is maintenance, THE Coaching_Adaptation_Engine SHALL emphasize balance and long-term sustainability
5. WHEN the Active_Goal is improved energy, THE Coaching_Adaptation_Engine SHALL emphasize energy patterns and optimization
6. WHEN the Active_Goal is better sleep, THE Coaching_Adaptation_Engine SHALL emphasize sleep hygiene and timing
7. THE Coaching_Adaptation_Engine SHALL store goal-specific coaching preferences in Goal_Context
8. THE AI_Conversation_Engine SHALL instruct Claude_AI to use goal-appropriate coaching style

### Requirement 150: Goal Progress Tracking and Metrics

**User Story:** As a user, I want to track progress specific to my current goal, so that I can see if I'm moving toward my objective

#### Acceptance Criteria

1. THE Health_App SHALL track progress metrics relevant to the Active_Goal
2. WHEN the Active_Goal is weight loss, THE Health_App SHALL track weight trends and caloric deficit consistency
3. WHEN the Active_Goal is muscle gain, THE Health_App SHALL track protein intake and strength indicators when available
4. WHEN the Active_Goal is maintenance, THE Health_App SHALL track weight stability and habit consistency
5. WHEN the Active_Goal is improved energy, THE Health_App SHALL track energy-related patterns and user-reported energy levels
6. WHEN the Active_Goal is better sleep, THE Health_App SHALL track sleep-related metrics from connected Health_Platforms
7. THE Dashboard SHALL display goal-specific progress metrics prominently
8. THE AI_Coach SHALL reference goal-specific progress metrics in coaching conversations

### Requirement 151: Historical Goal Context Retrieval

**User Story:** As a user returning to a previous goal, I want Amigo to remember what worked before, so that I can build on past success

#### Acceptance Criteria

1. WHEN a user sets an Active_Goal they have pursued before, THE AI_Conversation_Engine SHALL load the previous Goal_Context
2. THE AI_Coach SHALL reference successful patterns from previous instances of the same Health_Goal
3. THE AI_Coach SHALL acknowledge previous attempts at the goal and build on that experience
4. THE AI_Insight_Generator SHALL compare current progress to previous attempts at the same goal
5. THE Pattern_Analyzer SHALL identify which strategies worked well in previous goal instances
6. THE AI_Coach SHALL suggest starting strategies based on what worked in previous goal instances
7. THE Goal_Context SHALL include lessons learned from previous attempts at the same Health_Goal
8. THE AI_Conversation_Engine SHALL help users avoid patterns that didn't work in previous goal instances
