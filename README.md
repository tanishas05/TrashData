# TrashData

TrashData is an Android file cleaner app built in Kotlin. It scans your device for junk files like old files, large files, and duplicates, and helps you review and delete them easily. It also adds a lightweight AI layer to tag files with useful keywords for better understanding.

## Features

- Scan device storage for junk files  
- Detect:
  - Old files  
  - Large files  
  - Duplicate files (via hashing)  
- Delete selected files with confirmation  
- Search and filter files  
- Visual storage breakdown using a pie chart  
- Background scanning with progress updates  
- Notification when scan completes  
- AI-based keyword tagging (Groq / Llama) with offline fallback  

## How it works

1. **Scanning**
   - Uses a background worker (WorkManager) to scan storage
   - Filters relevant files (images, videos, docs, etc.)
   - Marks files as “junk” based on age and size

2. **Duplicate detection**
   - Uses MD5 hashing to group identical files
   - Only marks duplicates, doesn’t auto-delete

3. **AI keyword tagging**
   - Extracts text from files (PDF, DOCX, TXT, etc.)
   - Sends content/metadata to Groq API (Llama model)
   - Falls back to offline keyword extraction if API fails

4. **UI**
   - Main screen: start scan + quick categories
   - Files screen: list, filter, search, delete
   - Pie chart shows storage distribution

## Tech Stack

- Kotlin (Android)
- WorkManager (background tasks)
- PDFBox (PDF text extraction)
- OkHttp (API calls)
- MPAndroidChart (pie chart)
- Groq API (Llama model for keywords)

## Setup

1. Clone the repo
2. Open in Android Studio
3. Add your Groq API key in:
   ```kotlin
   GroqConfig.kt
   ```
4. Build and run on a device/emulator

## Permissions

- All Files Access (for scanning storage)
- Notifications (for scan completion alerts)

## Contributors
Built by Tanisha Sharma and Tavleen Kaur, as a part of the Mobile Application Development Course at IGDTUW 
