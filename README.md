<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" width="128" height="128" style="border-radius: 50%;" />

# InternetRadio

InternetRadio is an Android app for discovering and listening to radio stations from around the world, with features like favorites, recent history, and advanced search by country,language and tags.

> **Looking for a logo designer!** If you are a designer and would like to contribute a new logo or icon for the app, please open an issue

</div>

---

## Features

- **Global Radio Access**: Browse and stream thousands of radio stations globally.
- **Search, Filter & Sort**: Easily find stations by country, language, or tags, with advanced sorting options.
- **Library & Customization**: Manage station Library. Edit any station and configure the startup screen.
- **Android TV Support**: Enjoy a fully optimized and tailored experience on Android TV devices.
- **Android Auto Support**: Support for car screen via android auto.
- **FCast Support**: Cast and control radio stations on FCast-compatible devices.
- **Recent History**: Keep track of your recently played streams.
- **Backup & Restore**: Easily backup and restore your library and app settings.
- **Modern UI**: Built with Jetpack Compose for a smooth, intuitive, and responsive experience across phones, tablets, and widescreen devices.

## Screenshots

### Mobile

<div align="center">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="Browse Screen">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" alt="Player Screen">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" alt="Search Screen">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" alt="Settings Screen">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" alt="Library Screen">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" alt="Edit Station Screen">
</div>

### Android TV

<div align="center">
  <img width="48%" src="fastlane/metadata/android/en-US/images/tvScreenshots/1.png" alt="TV Browse Screen">
  <img width="48%" src="fastlane/metadata/android/en-US/images/tvScreenshots/2.png" alt="TV Player Screen">
  <img width="48%" src="fastlane/metadata/android/en-US/images/tvScreenshots/3.png" alt="Select Country">
  <img width="48%" src="fastlane/metadata/android/en-US/images/tvScreenshots/4.png" alt="Select Language">
</div>

## Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
- **Database**: [Room](https://developer.android.com/training/data-storage/room)
- **Dependency Injection**: [Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
- **API**: [Radio Browser API](https://www.radio-browser.info/)

## Building From Source

To build InternetRadio from source, ensure you have the latest version of Android Studio installed.

1. **Clone the repository**:
   ```bash
   git clone https://github.com/armanmaurya/internetradio.git
   ```
2. **Open the project** in Android Studio.
3. **Wait for Gradle sync** to complete.
4. **Run the app** on a physical device or emulator.

## Troubleshooting

If FCast is not discovering or connecting to your device on desktop Linux, your firewall may be blocking the required ports. This is a known issue with **UFW** and similar firewall tools. To allow FCast connections, run:

```bash
sudo ufw allow 46899/tcp
sudo ufw allow 46899/udp
```

---

## Contributing

Contributions are what make the open-source community such an amazing place to learn, inspire, and create. Any contributions you make are greatly appreciated.

Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

## Open Source Libraries

InternetRadio uses the following open-source libraries:

- [Android Jetpack Libraries](https://developer.android.com/jetpack) (Compose, Room, Media3, Lifecycle, Navigation, DataStore, etc.)
- [Hilt](https://developer.android.com/training/dependency-injection/hilt-android) - A dependency injection library for Android.
- [Radio Browser API](https://www.radio-browser.info/) - A community-driven effort to collect radio stations.
- [FCast](https://fcast.org/) - An open-source protocol for wireless streaming.

## License

This project is licensed under the GNU GPL v3 License - see the [LICENSE](LICENSE) file for details.
