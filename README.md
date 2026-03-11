# Echo Scrobbler

Echo Scrobbler is a desktop music tracking application for Linux. It monitors system media playback via playerctl and synchronizes listening data with the Last.fm platform.

## Current Status
This project is currently in active development. The core scrobbling logic and authentication flow are implemented, but the graphical user interface and advanced features are still being integrated.

## Tech Stack
* **Language**: Java 21
* **UI Framework**: JavaFX
* **Build Tool**: Maven
* **Environment**: Linux Mint
* **Dependencies**: Last.fm API, playerctl

## Implementation Progress
* [x] Basic system media detection via playerctl
* [x] Authentication token and session key generation
* [x] Real-time "Now Playing" status updates
* [x] Scrobble submission logic with time-threshold triggers
* [ ] Migration to a full MVC architecture
* [ ] Implementation of the refined UI/UX design
* [ ] Integration of user discovery and dashboard modules

## Configuration
To run this project, a `.env` file is required in the root directory with valid Last.fm API credentials:
- LASTFM_API_KEY
- LASTFM_SHARED_SECRET
- LASTFM_SESSION_KEY

---
*Developed as a portfolio project for Software Engineering.*
