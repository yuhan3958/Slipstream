package me.yuhan8954.slipstream;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Optional, temporary internet access via an installed cloudflared executable. */
final class InternetTunnel {
    private static final Pattern URL = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");
    private static Process process;

    static String start() throws Exception {
        process = new ProcessBuilder("cloudflared", "tunnel", "--url", "http://127.0.0.1:8788")
                .redirectErrorStream(true).start();
        Process child = process;
        Runtime.getRuntime().addShutdownHook(new Thread(InternetTunnel::stop));
        CompletableFuture<String> address = new CompletableFuture<>();
        Thread.startVirtualThread(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    var match = URL.matcher(line);
                    if (match.find()) address.complete(match.group());
                }
                address.completeExceptionally(new IllegalStateException("cloudflared exited"));
            } catch (Exception e) {
                address.completeExceptionally(e);
            }
        });
        try {
            return address.get(45, TimeUnit.SECONDS);
        } catch (Exception e) {
            stop();
            throw e;
        }
    }

    static synchronized void stop() {
        if (process != null) {
            process.destroyForcibly();
            process = null;
        }
    }

    private InternetTunnel() {}
}
