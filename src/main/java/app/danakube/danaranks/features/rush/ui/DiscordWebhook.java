package app.danakube.danaranks.features.rush.ui;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class DiscordWebhook {

    public static void sendDiscordWebhook(String webhookUrl, String content, Logger logger) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                URL url = URI.create(webhookUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String json = "{\"content\": \"" + content + "\"}";
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    // Success
                } else {
                    if (logger != null) {
                        logger.warning("[Rush Webhook] Request failed: Status " + code);
                    }
                }
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("[Rush Webhook] Error sending discord webhook: " + e.getMessage());
                }
            }
        });
    }
}
