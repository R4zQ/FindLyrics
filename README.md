# FindLyrics

JavaFX desktop app for searching song lyrics. Search for an artist via Spotify, pick a track, and get the lyrics.

Group university project.

## Requirements

- Java 23
- Maven

## Setup

1. Create a Spotify app at [developer.spotify.com](https://developer.spotify.com/dashboard) to get your credentials.
2. Copy the config template and fill in your credentials:

```bash
cp FindLyrics/config.properties.example FindLyrics/config.properties
```

Then edit `config.properties`:

```properties
spotify.client_id=YOUR_CLIENT_ID
spotify.client_secret=YOUR_CLIENT_SECRET
```

## Run

```bash
cd FindLyrics
./mvnw javafx:run
```

On Windows:

```bash
cd FindLyrics
mvnw.cmd javafx:run
```

## APIs used

- [Spotify Web API](https://developer.spotify.com/documentation/web-api) - artist and track search
- [lyrics.ovh](https://lyricsovh.docs.apiary.io/) - lyrics fetching
