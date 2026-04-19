# Echo Scrobbler

Echo Scrobbler is a lightweight desktop scrobbler for Linux that detects the currently playing track through MPRIS2 and sends scrobbles to Last.fm automatically.

The project was built with Java 21 and JavaFX, with a focus on a small native-feeling desktop experience, persistent Last.fm authentication, and simple background playback monitoring.

## Highlights

- Linux desktop app built with JavaFX
- Last.fm authentication flow with persistent local sessions
- Automatic track detection through `playerctl` and MPRIS2
- Real-time Last.fm "Now Playing" updates
- Automatic scrobbling based on playback duration
- Recent scrobbles dashboard
- System tray support for keeping the app running in the background

## Tech Stack

- **Language:** Java 21
- **UI:** JavaFX + FXML + CSS
- **Build tool:** Maven
- **API integration:** Last.fm API
- **Desktop integration:** MPRIS2 via `playerctl`
- **Platform:** Linux

## How It Works

Echo Scrobbler checks the active media session using `playerctl`, reads the current track metadata, and sends updates to Last.fm when playback changes.

When a track starts playing, the app:

1. Reads artist, title, album, playback status, and duration from MPRIS2.
2. Sends a "Now Playing" update to Last.fm.
3. Starts a scrobble timer.
4. Sends the final scrobble once the playback threshold is reached.

The Last.fm session is saved locally, so users only need to authorize the app once.

## Project Structure

```text
src/main/java/com/echoscrobbler
├── App.java                         # JavaFX entry point and application lifecycle
├── controller
│   ├── DashboardController.java      # Main dashboard UI logic
│   └── LoginController.java          # Last.fm login flow
├── model
│   ├── ScrobbleRecord.java           # Recent scrobble data model
│   └── Track.java                    # Current track data model
└── service
    ├── AuthService.java              # Last.fm auth and session persistence
    ├── LastFmClient.java             # Now Playing and scrobble API calls
    ├── LastFmService.java            # Last.fm read-only dashboard data
    └── ScrobbleTimer.java            # Scrobble threshold scheduling
```

## Requirements

- Linux
- Java 21+
- Maven
- `playerctl`
- A Last.fm API account

Install `playerctl` on Debian/Ubuntu-based distributions:

```bash
sudo apt install playerctl
```

## Configuration

Create a `.env` file in the project root:

```env
LASTFM_API_KEY=your_api_key_here
LASTFM_SHARED_SECRET=your_shared_secret_here
```

You can create Last.fm API credentials from the Last.fm API account page.

Sessions are stored locally at:

```text
~/.config/echo-scrobbler/session.json
```

Delete this file if you want to log out and authorize the app again.

## Running Locally

Clone the repository:

```bash
git clone https://github.com/ismajoventino/echo-scrobbler.git
cd echo-scrobbler
```

Run the app:

```bash
mvn javafx:run
```

On first launch, the app opens the Last.fm authorization page in your browser. After authorization, Echo Scrobbler stores the session locally and opens the dashboard.

## Building

Package the project with Maven:

```bash
mvn package
```

Run tests and compile checks:

```bash
mvn test
```

## Current Scope

Echo Scrobbler is currently focused on Linux desktop scrobbling through MPRIS2-compatible players such as Spotify, Firefox, Chromium, VLC, and other media apps supported by `playerctl`.

## Future Improvements

- Add automated tests for playback parsing and scrobble timing
- Improve error handling for missing players, network failures, and expired sessions
- Add a visible logout option in the UI
- Add configurable scrobble thresholds
- Provide packaged Linux builds

---

Built with Java, JavaFX, Maven, and the Last.fm API.
