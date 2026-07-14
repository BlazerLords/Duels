package com.meteordevelopments.duels.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.meteordevelopments.duels.DuelsPlugin;
import lombok.Getter;
import org.json.JSONObject;

@SuppressWarnings("all")
public class UpdateManager {
    private static final int NETWORK_TIMEOUT_MILLIS = 5_000;

    private final Logger logger;
    private final boolean stayUpToDate;
    private URL spigotUrl;
    private volatile boolean updateIsAvailable = false;
    private final String currentVersion;
    @Getter
    private volatile String latestVersion;

    public UpdateManager(DuelsPlugin main) {
        this.logger = main.getLogger();
        this.currentVersion = main.getDescription().getVersion();
        this.stayUpToDate = main.getConfiguration().isStayUpToDate();
    }

    public void checkForUpdate() {
        try {
            if (this.spigotUrl == null) {
                this.spigotUrl = new URL("https://version.itzadarsh-kushwaha.workers.dev/legacy/update.php");
            }
            URLConnection spigotConnection = openConnection(this.spigotUrl);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(spigotConnection.getInputStream()))) {
                this.latestVersion = reader.readLine();
            }
        } catch (IOException ex) {
            logger.warning("Could not check for a Duels update: " + ex.getMessage());
            return;
        }

        if (this.latestVersion != null && !this.getCurrentVersion().equals(this.latestVersion)) {
            this.setLatestVersion(this.latestVersion);
            this.setUpdateAvailability(true);
            if (stayUpToDate) {
                fetchAndDownloadFromModrinth();
            }
        }
    }

    private void fetchAndDownloadFromModrinth() {
        try {
            URL modrinthUrl = new URL("https://api.modrinth.com/v2/project/duels-optimised/version/" + this.latestVersion);
            URLConnection modrinthConnection = openConnection(modrinthUrl);
            StringBuilder response = new StringBuilder();
            String inputLine;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(modrinthConnection.getInputStream()))) {
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }

            // Parse JSON response
            String jsonResponse = response.toString();
            JSONObject jsonObject = new JSONObject(jsonResponse);
            String downloadUrl = jsonObject.getJSONArray("files").getJSONObject(0).getString("url");

            // Delete old jar files
            deleteOldFiles("plugins", "Duels-Optimised");

            // Download the latest plugin
            downloadLatestPlugin(downloadUrl);

        } catch (Exception ex) {
            logger.warning("Could not download the latest Duels update: " + ex.getMessage());
        }
    }

    private void deleteOldFiles(String directory, String prefix) {
        try (Stream<Path> paths = Files.list(Paths.get(directory))) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException ex) {
                            logger.warning("Could not delete old Duels JAR " + path + ": " + ex.getMessage());
                        }
                    });
        } catch (IOException ex) {
            logger.warning("Could not inspect the plugins directory for updates: " + ex.getMessage());
        }
    }

    private void downloadLatestPlugin(String downloadUrl) {
        try (BufferedInputStream in = new BufferedInputStream(openConnection(new URL(downloadUrl)).getInputStream());
             FileOutputStream fileOutputStream = new FileOutputStream("plugins/Duels-Optimised-" + this.latestVersion + ".jar")) {
            byte dataBuffer[] = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        } catch (IOException ex) {
            logger.warning("Could not write the latest Duels update: " + ex.getMessage());
        }
    }

    private URLConnection openConnection(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(NETWORK_TIMEOUT_MILLIS);
        connection.setReadTimeout(NETWORK_TIMEOUT_MILLIS);
        return connection;
    }

    public boolean updateIsAvailable() {
        return this.updateIsAvailable;
    }

    public void setUpdateAvailability(boolean availability) {
        this.updateIsAvailable = availability;
    }

    public String getCurrentVersion() {
        return this.currentVersion;
    }

    private void setLatestVersion(String latestVersion) {
        this.latestVersion = latestVersion;
    }
}
