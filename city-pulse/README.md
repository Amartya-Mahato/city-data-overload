# Bengaluru City Pulse

A comprehensive Flutter application for AI-powered smart city monitoring and citizen engagement in Bengaluru. This cross-platform app provides real-time, context-aware city information including traffic updates, emergency alerts, civic issues, and weather data.

## 🚀 Features

### Core Features
- **Real-time Dashboard**: Live critical updates and events with beautiful animations
- **Interactive Map**: Google Maps integration with event markers and filtering
- **AI-Powered Reporting**: Gemini Vision API integration for intelligent event categorization
- **Smart Alerts**: Location-based push notifications with customizable radius
- **Dark Mode**: Complete theme switching with Material Design 3
- **Responsive Design**: Works seamlessly on Android, iOS, Web, and Desktop
- **PWA Ready**: Progressive Web App capabilities for web deployment

### Technical Features
- **State Management**: Riverpod for reactive state management
- **Mock Data**: Comprehensive mock data service for development
- **Animations**: Dynamic UI reactions based on event types (shake, pulse, glow)
- **Offline Support**: Cached data and offline functionality
- **Accessibility**: WCAG 2.1 AA compliant design
- **Performance**: Optimized for all screen sizes with flutter_screenutil

## 🛠️ Tech Stack

### Frontend
- **Flutter 3.x** - Cross-platform development
- **Dart** - Programming language
- **Material Design 3** - UI framework
- **Riverpod** - State management
- **flutter_screenutil** - Responsive design

### Backend Integration (Mock)
- **Firebase** - Authentication, Database, Storage, Messaging
- **Google Maps** - Interactive maps and location services
- **Gemini AI** - Vision API for image analysis
- **Push Notifications** - Real-time alerts

## 📱 App Structure

```
lib/
├── components/          # Reusable UI components
│   ├── alert_banner.dart
│   ├── event_card.dart
│   └── bottom_nav_bar.dart
├── models/             # Data models
│   ├── event.dart
│   └── user.dart
├── providers/          # State management
│   ├── events_provider.dart
│   └── user_provider.dart
├── screens/            # Main app screens
│   ├── dashboard_screen.dart
│   ├── alerts_screen.dart
│   ├── map_view_screen.dart
│   ├── report_screen.dart
│   └── settings_screen.dart
├── services/           # Business logic
│   ├── mock_data_service.dart
│   └── location_service.dart
├── themes/             # App theming
│   └── app_theme.dart
└── main.dart          # App entry point
```

## 🚀 Getting Started

### Prerequisites
- Flutter SDK (3.0.0 or later)
- Dart SDK (2.17.0 or later)
- Android Studio / VS Code
- Git

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/bengaluru_city_pulse.git
   cd bengaluru_city_pulse
   ```

2. **Install dependencies**
   ```bash
   flutter pub get
   ```

3. **Generate code** (for JSON serialization)
   ```bash
   flutter packages pub run build_runner build
   ```

4. **Run the app**
   ```bash
   flutter run
   ```

## 🎨 Design System

### Color Palette
- **Primary Green**: #2E7D32 (Bengaluru-inspired)
- **Earthy Brown**: #5D4037
- **Misty Blue**: #1976D2
- **Alert Red**: #D32F2F
- **Warning Orange**: #FF9800
- **Success Green**: #4CAF50

## 📊 Mock Data

The app includes comprehensive mock data for development:

### Events
- Traffic incidents with real Bengaluru locations
- Emergency situations with severity levels
- Weather alerts with impact radius
- Infrastructure issues with categories

### User Data
- User preferences and settings
- Reputation and report history
- Customizable alert categories

## 🎯 Features in Detail

### 1. Dashboard
- Welcome message with user name
- Quick stats cards (total events, severity breakdown)
- Recent events with animations
- Critical alert banners

### 2. Alerts Screen
- Filterable event list
- Category and severity filters
- Pull-to-refresh functionality
- Event detail modal

### 3. Map View
- Interactive Google Maps
- Color-coded severity markers
- Event clustering
- Custom map styling

### 4. Report Screen
- Image/video upload
- AI-powered categorization
- Form validation
- Location detection

### 5. Settings
- User profile management
- Notification preferences
- Theme switching
- Alert radius configuration

## 🌐 PWA Features

The app is PWA-ready with:
- Service Worker for offline functionality
- Web App Manifest for installability
- Responsive design for all screen sizes
- Push notification support

## 🔮 Future Enhancements

### Integration Opportunities
- **Real Firebase Integration**: Replace mock data with live Firebase
- **Actual Gemini API**: Implement real AI vision analysis
- **Live Maps Data**: Integration with traffic and transit APIs
- **Push Notifications**: Real-time alert system
- **User Authentication**: Social login and profile management

### Feature Additions
- **Offline Mode**: Complete offline functionality
- **Social Features**: User ratings and comments
- **Analytics**: Usage tracking and insights
- **Multilingual**: Support for Kannada and Hindi
- **Voice Commands**: Accessibility improvements

## 🧪 Testing

Run tests with:
```bash
flutter test
```

## 📱 Building for Production

### Android
```bash
flutter build apk --release
flutter build appbundle --release
```

### iOS
```bash
flutter build ios --release
```

### Web
```bash
flutter build web --release
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Flutter team for the amazing framework
- Google for Material Design 3
- Firebase for backend services
- Unsplash for demo images
- Bengaluru city for inspiration

---

**Made with ❤️ for Bengaluru citizens**
