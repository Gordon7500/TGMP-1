# TGMP-1 (Tuned Music Player)

A modern music player app for Android built with Jetpack Compose, featuring advanced audio controls and dual music source support.

## Features

### Audio Playback
- **High-Quality Audio Engine**: Advanced audio processing with customizable equalizer
- **Bass Boost**: Enhance low frequencies with adjustable strength (0-1000 millibels)
- **10-Band Equalizer**: Fine-tune audio to your preference
- **Multiple Output Devices**: Seamlessly switch between speakers, headphones, and USB audio
- **Media Controls**: Play, pause, seek, and skip with responsive controls

### Music Library
- **Telegram Integration**: Pull music from your Telegram channels using a bot token
- **Local Storage Access** (NEW): Browse and play audio files directly from your device
- **Search & Sort**: Find tracks by title or artist, sort by date added, title, artist, or duration
- **Advanced Filtering**: Smart search across both Telegram and local sources

### Local Storage Features (NEW)
- Access music files directly from device memory
- Query all audio files or search by title/artist
- Filter by custom directories
- Seamless integration with existing playback system
- Toggle local storage on/off from settings

### User Interface
- **Material Design 3**: Beautiful, modern UI with dark theme
- **Audio Visualizer**: Real-time visualization of audio waveforms
- **Compact Player Bar**: Minimal yet functional playback controls
- **Settings Panel**: Configure Telegram bot, channels, and audio output
- **Equalizer Sheet**: Adjust audio effects on-the-fly

## Project Structure

```
app/src/main/
├── java/com/tuned/app/
│   ├── MainActivity.kt              # Entry point
│   ├── PlayerViewModel.kt           # Main state management
│   ├── audio/
│   │   ├── AudioEngine.kt          # Audio playback engine
│   │   ├── AudioEffectsController.kt # Equalizer & bass boost
│   │   ├── OutputDeviceRouter.kt   # Audio device management
│   │   └── PlaybackService.kt      # Background playback service
│   ├── data/
│   │   ├── Track.kt                # Track data model
│   │   ├── Store.kt                # Local preferences storage
│   │   └── LocalTrackProvider.kt   # Local file access (NEW)
│   ├── telegram/
│   │   └── TelegramClient.kt       # Telegram bot integration
│   └── ui/
│       ├── LibraryScreen.kt        # Main music library UI
│       ├── PlayerBar.kt            # Playback controls UI
│       ├── EqualizerSheet.kt       # Equalizer settings UI
│       ├── SettingsSheet.kt        # Settings UI
│       ├── Visualizer.kt           # Audio waveform visualizer
│       └── Theme.kt                # Design system
├── java/com/tgmp/storage/
│   └── StorageAccessManager.kt     # Device storage access (NEW)
└── res/
    ├── values/
    ├── drawable/
    └── mipmap/
```

## Permissions Required

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
```

## Getting Started

### Prerequisites
- Android Studio Flamingo or later
- Android 8.0+ (API level 26+)
- Kotlin 1.9+

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/Gordon7500/TGMP-1.git
   cd TGMP-1
   ```

2. **Configure Telegram Bot (Optional)**
   - Create a bot using [@BotFather](https://t.me/botfather) on Telegram
   - Copy your bot token
   - In the app settings, paste the token and add channel IDs

3. **Enable Local Storage Access**
   - Grant the app permission to access audio files on first run
   - Toggle "Local Storage" in the settings
   - Your device's audio files will automatically appear in the library

4. **Build and Run**
   ```bash
   ./gradlew build
   ./gradlew installDebug
   ```

## Dependencies

- **Jetpack Compose** - Modern UI framework
- **Lifecycle** - State management & lifecycle awareness
- **DataStore** - Secure preferences storage
- **Coroutines** - Asynchronous programming
- **OkHttp** - HTTP client for Telegram API
- **Android Media** - Media playback framework

## Usage

### Playing Local Music
1. Open the app
2. Tap the Settings icon (⚙️)
3. Toggle "Local Storage" ON
4. Your device's audio files will appear in the library
5. Tap any track to play

### Configuring Telegram
1. Get a bot token from [@BotFather](https://t.me/botfather)
2. Tap Settings → Enter your bot token
3. Add the channel IDs you want to pull music from
4. The app will sync music from those channels

### Audio Effects
1. Tap the Tune icon (🎚️) to open the equalizer
2. Adjust the 10 bands to your preference
3. Enable Bass Boost for extra low-end punch

## Build Information

- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34
- **Kotlin**: 1.9.14
- **Compose**: 2024.06.00

## License

MIT License - See LICENSE file for details

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues for bugs and feature requests.

## Support

For issues, questions, or suggestions, please open a GitHub issue or contact the maintainer.
