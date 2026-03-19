# Echo Scrobbler

A minimal Last.fm scrobbler for Linux, built with Java and JavaFX.

Detects music playing on your system via MPRIS2 and automatically scrobbles to your Last.fm profile 

---

## Features

- Detects any media player that supports MPRIS2 (Spotify, Firefox, Chromium, VLC, etc.)
- Automatic scrobbling 
- Real-time "Now Playing" updates on Last.fm
- Recent scrobbles list 
- Persistent session. Login once, stays authenticated

## Tech Stack

- **Language**: Java 21
- **UI**: JavaFX
- **Build**: Maven
- **Platform**: Linux

## Requirements

- Java 21+
- Maven
- [`playerctl`](https://github.com/altdesktop/playerctl)

```bash
sudo apt install playerctl
```

## Setup
1. Clone the repo
```bash
git clone https://github.com/ismajoventino/echo-scrobbler.git
cd echo-scrobbler
```
2. Run
```bash
mvn javafx:run &
```
On first launch, you'll be prompted to authorize the app via your browser. After that, the session is saved locally and you won't need to log in again.

## Configuration
Sessions are stored at `~/.config/echo-scrobbler/session.json`. Delete this file to log out.

---
*Built for Linux · Java · JavaFX* 
