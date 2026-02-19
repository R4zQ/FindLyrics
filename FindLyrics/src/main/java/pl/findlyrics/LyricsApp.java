package pl.findlyrics;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;

@SuppressWarnings("ALL")
public class LyricsApp extends Application {
    private static final String CLIENT_ID;
    private static final String CLIENT_SECRET;

    static {
        Properties props = new Properties();
        try (FileInputStream input = new FileInputStream("config.properties")) {
            props.load(input);
        } catch (Exception e) {
            throw new RuntimeException("Brak pliku config.properties. Skopiuj config.properties.example i uzupełnij swoje kredencjale Spotify.", e);
        }
        CLIENT_ID = props.getProperty("spotify.client_id");
        CLIENT_SECRET = props.getProperty("spotify.client_secret");
    }
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String SPOTIFY_SEARCH_URL = "https://api.spotify.com/v1/search?q=%s&type=artist";
    private static final String LYRICS_OVH_API = "https://api.lyrics.ovh/v1/%s/%s";

    private String accessToken;
    private String selectedArtistName = null;

    public static class SongItem {
        public final String title;
        public final String albumImageUrl;

        public SongItem(String title, String albumImageUrl) {
            this.title = title;
            this.albumImageUrl = albumImageUrl;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Nagłówek aplikacji
        Label titleLabel = new Label("Find Your Lyrics");
        titleLabel.getStyleClass().add("heading-label");

        // Sekcja szukania
        TextField artistTextField = new TextField();
        artistTextField.setPromptText("Wpisz nazwisko artysty...");
        artistTextField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(artistTextField, Priority.ALWAYS);

        Button searchButton = new Button("Szukaj");
        searchButton.setMinWidth(90);
        searchButton.setPrefWidth(100);

        HBox searchBox = new HBox(7, artistTextField, searchButton);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setMaxWidth(Double.MAX_VALUE);

        // Lista utworów
        Label songListLabel = new Label("Lista utworów:");
        songListLabel.setStyle("-fx-font-weight: bold;");

        ListView<SongItem> songListView = new ListView<>();
        songListView.setMinHeight(120);
        songListView.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(songListView, Priority.ALWAYS);

        VBox songListSection = new VBox(5, songListLabel, songListView);
        songListSection.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(songListSection, Priority.ALWAYS);

        // Sekcja lewa - card
        VBox leftColumn = new VBox(12, searchBox, songListSection);
        leftColumn.setPadding(new Insets(10));
        leftColumn.setMinWidth(180);
        leftColumn.setMaxWidth(Double.MAX_VALUE);
        leftColumn.getStyleClass().add("section-card");
        HBox.setHgrow(leftColumn, Priority.SOMETIMES);

        // Okładka albumu (mała, trzyma się prawej)
        ImageView albumArtView = new ImageView();
        albumArtView.setFitWidth(80);
        albumArtView.setFitHeight(80);
        albumArtView.setPreserveRatio(true);
        albumArtView.setSmooth(true);

        // Nagłówek sekcji tekstu: tytuł po lewej, okładka po prawej
        Label lyricsLabel = new Label("Tekst piosenki:");
        lyricsLabel.setStyle("-fx-font-weight: bold;");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        HBox headerBox = new HBox(lyricsLabel, headerSpacer, albumArtView);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setSpacing(10);

        // Pole tekstowe na cały dostępny obszar, rozciąga się z oknem
        TextArea lyricsArea = new TextArea();
        lyricsArea.setWrapText(true);
        lyricsArea.setEditable(false);
        lyricsArea.setMinHeight(120);
        lyricsArea.setMaxHeight(Double.MAX_VALUE);
        lyricsArea.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(lyricsArea, Priority.ALWAYS);

        // Przycisk kopiowania pod spodem, na środku
        Button copyButton = new Button("Kopiuj tekst");
        copyButton.setPrefWidth(140);
        copyButton.setOnAction(_ -> {
            String text = lyricsArea.getText();
            if (text != null && !text.isEmpty()) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(text);
                clipboard.setContent(content);
            }
        });
        HBox copyBox = new HBox(copyButton);
        copyBox.setAlignment(Pos.CENTER);
        copyBox.setPadding(new Insets(4, 0, 0, 0));

        // Sekcja tekstu: nagłówek (tytuł + okładka po prawej), pole tekstowe, przycisk
        VBox lyricsSection = new VBox(8, headerBox, lyricsArea, copyBox);
        lyricsSection.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(lyricsSection, Priority.ALWAYS);

        // Prawa kolumna - card
        VBox rightColumn = new VBox(lyricsSection);
        rightColumn.setPadding(new Insets(10));
        rightColumn.setMinWidth(250);
        rightColumn.setMaxWidth(Double.MAX_VALUE);
        rightColumn.getStyleClass().add("section-card");
        HBox.setHgrow(rightColumn, Priority.ALWAYS);

        // Layout główny - dwie rozciągliwe kolumny
        HBox mainContent = new HBox(16, leftColumn, rightColumn);
        mainContent.setAlignment(Pos.TOP_CENTER);
        mainContent.setPadding(new Insets(10));
        mainContent.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(mainContent, Priority.ALWAYS);

        // Główne okno
        VBox root = new VBox(18, titleLabel, mainContent);
        root.setPadding(new Insets(16));
        root.setAlignment(Pos.TOP_CENTER);
        root.setMinHeight(350);
        root.setMinWidth(600);
        VBox.setVgrow(mainContent, Priority.ALWAYS);

        Scene scene = new Scene(root, 820, 470);

        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm());

        // --- LOGIKA ---
        searchButton.setOnAction(_ -> {
            String artistName = artistTextField.getText().trim();
            if (!artistName.isEmpty()) {
                searchArtist(artistName, artistTextField, songListView, lyricsArea, albumArtView);
            } else {
                lyricsArea.setText("Proszę wpisać nazwisko artysty.");
                albumArtView.setImage(null);
            }
        });

        songListView.setOnMouseClicked(_ -> {
            SongItem selected = songListView.getSelectionModel().getSelectedItem();
            if (selected != null && selectedArtistName != null) {
                if (selected.albumImageUrl != null && !selected.albumImageUrl.isEmpty()) {
                    albumArtView.setImage(new Image(selected.albumImageUrl, true));
                } else {
                    albumArtView.setImage(null);
                }
                fetchLyrics(selectedArtistName, selected.title, lyricsArea);
            }
        });

        primaryStage.setTitle("Find Your Lyrics");
        primaryStage.setMinWidth(720);
        primaryStage.setMinHeight(420);
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

    private void searchArtist(String artistName, TextField artistTextField, ListView<SongItem> songListView, TextArea lyricsArea, ImageView albumArtView) {
        if (accessToken == null) {
            lyricsArea.setText("Trwa uzyskiwanie dostępu do Spotify...");
            albumArtView.setImage(null);
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

                        if (!artists.isEmpty()) {
                            JSONObject artistObj = artists.getJSONObject(0);
                            String artistId = artistObj.getString("id");
                            selectedArtistName = artistObj.getString("name");
                            Platform.runLater(() -> artistTextField.setText(selectedArtistName));
                            fetchTopTracks(artistId, songListView, lyricsArea, albumArtView);
                        } else {
                            Platform.runLater(() -> {
                                lyricsArea.setText("Nie znaleziono artysty.");
                                albumArtView.setImage(null);
                                songListView.getItems().clear();
                            });
                        }
                    })
                    .exceptionally(e -> {
                        e.printStackTrace();
                        Platform.runLater(() -> {
                            lyricsArea.setText("Błąd wyszukiwania artysty.");
                            albumArtView.setImage(null);
                            songListView.getItems().clear();
                        });
                        return null;
                    });
        } catch (Exception e) {
            e.printStackTrace();
            lyricsArea.setText("Błąd kodowania nazwy artysty.");
            albumArtView.setImage(null);
        }
    }

    private void fetchTopTracks(String artistId, ListView<SongItem> songListView, TextArea lyricsArea, ImageView albumArtView) {
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

                    if (!tracks.isEmpty()) {
                        Platform.runLater(() -> {
                            songListView.getItems().clear();
                            for (int i = 0; i < tracks.length(); i++) {
                                JSONObject track = tracks.getJSONObject(i);
                                String songName = track.getString("name");
                                String albumImageUrl = "";
                                try {
                                    JSONArray images = track.getJSONObject("album").getJSONArray("images");
                                    if (!images.isEmpty()) {
                                        albumImageUrl = images.getJSONObject(0).getString("url");
                                    }
                                } catch (Exception ex) {
                                    albumImageUrl = "";
                                }
                                songListView.getItems().add(new SongItem(songName, albumImageUrl));
                            }
                            lyricsArea.clear();
                            albumArtView.setImage(null);
                        });
                    } else {
                        Platform.runLater(() -> {
                            lyricsArea.setText("Nie znaleziono utworów dla tego artysty.");
                            albumArtView.setImage(null);
                            songListView.getItems().clear();
                        });
                    }
                })
                .exceptionally(e -> {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        lyricsArea.setText("Błąd pobierania utworów.");
                        albumArtView.setImage(null);
                        songListView.getItems().clear();
                    });
                    return null;
                });
    }

    private void fetchLyrics(String artist, String title, TextArea lyricsArea) {
        new Thread(() -> {
            try {
                String cleanedTitle = title.replaceAll("\\(.*?\\)", "").replaceAll(" - .*", "").trim();

                String urlStr = String.format(LYRICS_OVH_API,
                        URLEncoder.encode(artist, StandardCharsets.UTF_8),
                        URLEncoder.encode(cleanedTitle, StandardCharsets.UTF_8));

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

                lyrics = lyrics.replaceAll("(?m)(\\n\\s*){3,}", "\n\n");

                String finalLyrics = lyrics;
                Platform.runLater(() -> lyricsArea.setText(finalLyrics));
            } catch (Exception e) {
                Platform.runLater(() -> lyricsArea.setText("Nie znaleziono tekstu lub błąd sieci."));
            }
        }).start();
    }
}
