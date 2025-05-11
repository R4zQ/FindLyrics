package pl.neratica.findlyrics;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class LyricsApp extends Application {
    private static final String CLIENT_ID = "e4ce3fbeb26b4e209582f311a4b692a2";
    private static final String CLIENT_SECRET = "8768a184e8e247a78d369c9d0c11da4e";
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String SPOTIFY_SEARCH_URL = "https://api.spotify.com/v1/search?q=%s&type=artist";
    private static final String LYRICS_OVH_API = "https://api.lyrics.ovh/v1/%s/%s";

    private String accessToken;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        VBox layout = new VBox(10);
        TextField artistTextField = new TextField();
        artistTextField.setPromptText("Wpisz nazwisko artysty...");
        ListView<String> songListView = new ListView<>();
        TextArea lyricsArea = new TextArea();
        lyricsArea.setWrapText(true);
        Button searchButton = new Button("Szukaj");

        layout.getChildren().addAll(artistTextField, searchButton, songListView, lyricsArea);
        Scene scene = new Scene(layout, 600, 400);

        searchButton.setOnAction(event -> {
            String artistName = artistTextField.getText().trim();
            if (!artistName.isEmpty()) {
                searchArtist(artistName, songListView, lyricsArea);
            } else {
                lyricsArea.setText("Proszę wpisać nazwisko artysty.");
            }
        });

        songListView.setOnMouseClicked(event -> {
            String selectedSong = songListView.getSelectionModel().getSelectedItem();
            if (selectedSong != null && !artistTextField.getText().isEmpty()) {
                fetchLyrics(artistTextField.getText().trim(), selectedSong, lyricsArea);
            }
        });

        scene.getStylesheets().add("style.css");
        primaryStage.setTitle("Find Your Lyrics");
        primaryStage.setScene(scene);
        primaryStage.show();

        getAccessToken();
    }

    private void getAccessToken() {
        HttpClient client = HttpClient.newHttpClient();
        String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedCredentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Authorization", "Basic " + encodedCredentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(response -> {
                    JSONObject jsonResponse = new JSONObject(response);
                    accessToken = jsonResponse.getString("access_token");
                })
                .exceptionally(e -> {
                    e.printStackTrace();
                    return null;
                });
    }

    private void searchArtist(String artistName, ListView<String> songListView, TextArea lyricsArea) {
        if (accessToken == null) {
            lyricsArea.setText("Trwa uzyskiwanie dostępu do Spotify...");
            return;
        }

        try {
            String encodedArtistName = URLEncoder.encode(artistName, StandardCharsets.UTF_8);
            String url = String.format(SPOTIFY_SEARCH_URL, encodedArtistName);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenAccept(response -> {
                        JSONObject jsonResponse = new JSONObject(response);
                        JSONArray artists = jsonResponse.getJSONObject("artists").getJSONArray("items");

                        if (artists.length() > 0) {
                            String artistId = artists.getJSONObject(0).getString("id");
                            fetchTopTracks(artistId, songListView, lyricsArea);
                        } else {
                            Platform.runLater(() -> lyricsArea.setText("Nie znaleziono artysty."));
                        }
                    })
                    .exceptionally(e -> {
                        e.printStackTrace();
                        Platform.runLater(() -> lyricsArea.setText("Błąd wyszukiwania artysty."));
                        return null;
                    });
        } catch (Exception e) {
            e.printStackTrace();
            lyricsArea.setText("Błąd kodowania nazwy artysty.");
        }
    }

    private void fetchTopTracks(String artistId, ListView<String> songListView, TextArea lyricsArea) {
        String url = "https://api.spotify.com/v1/artists/" + artistId + "/top-tracks?market=US";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(response -> {
                    JSONObject jsonResponse = new JSONObject(response);
                    JSONArray tracks = jsonResponse.getJSONArray("tracks");

                    if (tracks.length() > 0) {
                        Platform.runLater(() -> {
                            songListView.getItems().clear();
                            for (int i = 0; i < tracks.length(); i++) {
                                String songName = tracks.getJSONObject(i).getString("name");
                                songListView.getItems().add(songName);
                            }
                        });
                    } else {
                        Platform.runLater(() -> lyricsArea.setText("Nie znaleziono utworów dla tego artysty."));
                    }
                })
                .exceptionally(e -> {
                    e.printStackTrace();
                    Platform.runLater(() -> lyricsArea.setText("Błąd pobierania utworów."));
                    return null;
                });
    }

    private void fetchLyrics(String artist, String title, TextArea lyricsArea) {
        new Thread(() -> {
            try {
                String urlStr = String.format(LYRICS_OVH_API,
                        URLEncoder.encode(artist, StandardCharsets.UTF_8),
                        URLEncoder.encode(title, StandardCharsets.UTF_8));

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                String json = response.toString();
                String lyrics = json.replaceAll(".*\\\"lyrics\\\":\\\"", "")
                        .replaceAll("\\\"\\s*\\}$", "")
                        .replaceAll("\\\\n", "\n")
                        .replaceAll("\\\\r", "")
                        .replaceAll("\\\\\"", "\"");

                Platform.runLater(() -> lyricsArea.setText(lyrics));
            } catch (Exception e) {
                Platform.runLater(() -> lyricsArea.setText("Nie znaleziono tekstu lub błąd sieci."));
            }
        }).start();
    }
}
