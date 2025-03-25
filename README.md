# CS501 Final Project - AI-Powered Journaling App  

## 📖 Introduction  
Journaling can be a powerful tool for self-reflection and personal growth, but many people struggle with consistency and knowing what to write. We decided to make a journaling app that makes the process more engaging.  

It will incorporate **AI-generated prompts** and **motivational features**. Similar to the app *BeReal*, each journal entry has a **photo to capture the moment** before the user starts writing.  

## ✨ Features  
The app provides several features to encourage journaling and make the experience more interactive:  

- **AI Guidance:** The app offers AI-generated prompts to help users decide what to write.  
- **Push Notifications:** Reminders keep users consistent with their journaling habit.  
- **Streak System:** Encourages users to maintain regular journaling.  
- **Calendar View:** Allows users to revisit past entries at any time.  
- **Delayed Commenting:** Enables reflection on older journal entries.  
- **Memories Feature:** Similar to *Snapchat Memories*, past journal entries from the same date in previous years are resurfaced, creating nostalgia and increasing self-awareness.  
- **AI Reflections:** The app provides automatic insights on completed journal entries, offering personalized feedback and self-improvement suggestions.  

## 📸 Photo-First Journaling  
The app utilizes the **device’s camera**, requiring users to take a **photo before starting a journal entry**. This ensures that users **capture a real-life moment** before writing, making each entry more meaningful.  

## 🔧 Backend & AI Integration  
The backend of the app will be built using **Firebase** as the database to store:  
- User accounts  
- Journal entries  
- Push notification data  

**Firebase Cloud Messaging** will handle reminders, ensuring users are notified at their preferred journaling time.  

The **Gemini API** will be responsible for AI features, including:  
- **Generating writing prompts** to inspire users  
- **Providing reflections** on past journal entries for deeper insights  

## 📱 Target Platforms  
Testing will focus on **smartphones** and **tablets** to optimize the user experience:  

- **Smartphones** are the most commonly used mobile devices and provide a convenient platform for quick journal entries. They also make it easier for users to capture photos.  
- **Tablets** offer a **larger screen**, making them ideal for a more immersive writing experience.  

The interface will be designed to **adapt to different device sizes and orientations** for seamless usability.  

## 🏠 User Interface & Experience  
The app’s interface will be intuitive and structured around journaling habits:  

- **Home Screen:** A **calendar-based interface** where users can access past entries.  
- **Bottom Navigation Bar:** Provides quick access to **new journal entries, streak tracking, and settings**.  
- **New Entry Flow:**  
  1. **Capture a photo** 📸 before writing.  
  2. **Write freely** or generate an **AI prompt** ✍️ for inspiration.  
  3. **Access AI-generated reflections** 🤖 after completing an entry.  
