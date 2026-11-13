package me.yuhan8954.slipstream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.KeyStore;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public class Slipstream {
    private static final int PORT = 8787;
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024 * 1024;

    private static final Path DOWNLOAD_DIR = Path.of("downloads").toAbsolutePath();

    private static final String TOKEN = createToken();

    public static void main(String[] args) throws Exception {
        Files.createDirectories(DOWNLOAD_DIR);

        HttpsServer server = createHttpsServer();

        server.createContext("/", Slipstream::handleIndex);

        server.createContext("/api/upload", Slipstream::handleUpload);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.start();

        System.out.println();
        System.out.println("====================================");
        System.out.println("       SLIPSTREAM v0.1");
        System.out.println("====================================");
        System.out.println("Server port : " + PORT);
        System.out.println("Save folder : " + DOWNLOAD_DIR);
        System.out.println();
        System.out.println("Open on your phone:");

        List<String> addresses = getAddresses();
        addresses.forEach(System.out::println);
        SlipstreamControlWindow.show(addresses, DOWNLOAD_DIR, () -> {
            server.stop(0);
            executor.shutdownNow();
            System.exit(0);
        });

        System.out.println();
        System.out.println("Press Ctrl+C to stop.");
        System.out.println("====================================");
    }

    private static HttpsServer createHttpsServer() throws Exception {
        String keyStorePath = System.getenv("SLIPSTREAM_KEYSTORE");
        String keyStorePassword = System.getenv("SLIPSTREAM_KEYSTORE_PASSWORD");

        if (keyStorePath == null || keyStorePassword == null) {
            throw new IllegalStateException(
                    "HTTPS requires SLIPSTREAM_KEYSTORE=<PKCS12 file> and SLIPSTREAM_KEYSTORE_PASSWORD=<password>."
            );
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        char[] password = keyStorePassword.toCharArray();
        try (InputStream input = Files.newInputStream(Path.of(keyStorePath))) {
            keyStore.load(input, password);
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        HttpsServer server = HttpsServer.create(new InetSocketAddress(PORT), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        return server;
    }

    private static List<String> getAddresses() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        List<String> urls = new ArrayList<>();

        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();

            if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                continue;
            }

            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();

            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                    urls.add("https://" + address.getHostAddress() + ":" + PORT + "/?token=" + TOKEN);
                }
            }
        }

        if (urls.isEmpty()) {
            urls.add("https://127.0.0.1:" + PORT + "/?token=" + TOKEN);
        }
        return urls;
    }

    private static void handleIndex(HttpExchange httpExchange) throws IOException {
        if (!httpExchange.getRequestMethod().equals("GET")) {
            send(httpExchange, 405, "Method not allowed");
            return;
        }

        if (!httpExchange.getRequestURI().getPath().equals("/")) {
            send(httpExchange, 404, "Not found");
            return;
        }

        if (!authenticated(httpExchange)) {
            send(httpExchange, 403, "Invalid access token");
            return;
        }

        httpExchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        httpExchange.getResponseHeaders().set("Referer-Policy", "no-referer");
        httpExchange.getResponseHeaders().set("Cache-Control", "no-store");

        byte[] content = HTML.getBytes(StandardCharsets.UTF_8);

        httpExchange.sendResponseHeaders(200, content.length);

        try (OutputStream out = httpExchange.getResponseBody()) {
            out.write(content);
        }
    }

    private static void handleUpload(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, 405, "Method not allowed");
            return;
        }

        if (!authenticated(exchange)) {
            send(exchange, 403, "Invalid access token");
            return;
        }

        String encodedName = exchange.getRequestHeaders()
                .getFirst("X-File-Name");

        if (encodedName == null || encodedName.isBlank()) {
            send(exchange, 400, "Missing file name");
            return;
        }

        String fileName;

        try {
            fileName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            send(exchange, 400, "Invalid file name");
            return;
        }

        // Remove path separators and unsafe characters.
        fileName = fileName.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();

        if (fileName.length() > 100) {
            fileName = fileName.substring(fileName.length() - 100);
        }

        if (fileName.isBlank() || fileName.equals(".") || fileName.equals("..")) {
            fileName = "unnamed";
        }

        // Unique prefix prevents accidental overwrites.
        String storedName = UUID.randomUUID().toString().substring(0, 8) + "_" + fileName;

        Path destination = DOWNLOAD_DIR.resolve(storedName);

        long totalBytes = 0;
        boolean completed = false;

        try {
            try (InputStream input = exchange.getRequestBody();
                 OutputStream output = Files.newOutputStream(
                         destination,
                         StandardOpenOption.CREATE_NEW,
                         StandardOpenOption.WRITE
                 )) {

                byte[] buffer = new byte[64 * 1024];
                int count;

                while ((count = input.read(buffer)) != -1) {
                    if (totalBytes > MAX_FILE_SIZE - count) {
                        send(exchange, 413, "File too large");
                        return;
                    }

                    output.write(buffer, 0, count);
                    totalBytes += count;
                }
            }

            completed = true;

            System.out.printf(
                    "[UPLOAD] %s (%d bytes)%n",
                    storedName, totalBytes
            );

            send(exchange, 200, "Uploaded: " + storedName);

        } finally {
            if (!completed) {
                Files.deleteIfExists(destination);
            }
        }
    }

    private static boolean authenticated(HttpExchange exchange) {
        String supplied;

        if (exchange.getRequestURI().getPath().equals("/api/upload")) {
            supplied = exchange.getRequestHeaders().getFirst("X-Slipstream-Token");
        } else {
            supplied = getQueryParameter(exchange.getRequestURI().getRawQuery(), "token");
        }

        if (supplied == null) {
            return false;
        }

        return MessageDigest.isEqual(
                TOKEN.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String getQueryParameter(String query, String name) {
        if (query == null) return null;

        for (String part : query.split("&")) {
            int separator = part.indexOf('=');

            if (separator < 0) continue;

            String key = part.substring(0, separator);

            if (key.equals(name)) {
                return URLDecoder.decode(
                        part.substring(separator + 1),
                        StandardCharsets.UTF_8
                );
            }
        }

        return null;
    }

    private static String createToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static void send(HttpExchange exchange, int status, String message) throws IOException {

        byte[] content = message.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type", "text/plain; charset=utf-8"
        );
        exchange.getResponseHeaders().set(
                "Cache-Control", "no-store"
        );

        exchange.sendResponseHeaders(status, content.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(content);
        }
    }

    private static final String HTML = """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport"
                    content="width=device-width,initial-scale=1">
              <meta name="referrer" content="no-referrer">
              <title>Slipstream</title>
              <style>
                :root {
                  color-scheme: dark;
                  font-family: system-ui, sans-serif;
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  background: #0c101a;
                  color: #eef2ff;
                  min-height: 100vh;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  padding: 20px;
                }
                main {
                  width: 100%;
                  max-width: 480px;
                  padding: 28px;
                  background: #171d2b;
                  border: 1px solid #30394c;
                  border-radius: 20px;
                }
                h1 {
                  margin: 0;
                  font-size: 32px;
                  letter-spacing: -1px;
                }
                .subtitle {
                  margin: 8px 0 26px;
                  color: #9caac5;
                }
                .dropzone {
                  border: 2px dashed #526487;
                  border-radius: 14px;
                  padding: 36px 16px;
                  text-align: center;
                  cursor: pointer;
                  background: #20283a;
                }
                .dropzone.active {
                  border-color: #78a5ff;
                  background: #293856;
                }
                .dropzone strong {
                  display: block;
                  font-size: 18px;
                  margin-bottom: 8px;
                }
                .dropzone span {
                  color: #aab6ce;
                  font-size: 13px;
                }
                button {
                  border: 0;
                  width: 100%;
                  margin-top: 18px;
                  padding: 14px;
                  border-radius: 10px;
                  background: #5e91ff;
                  color: #081328;
                  font-size: 16px;
                  font-weight: bold;
                  cursor: pointer;
                }
                button:disabled {
                  opacity: .45;
                  cursor: not-allowed;
                }
                progress {
                  width: 100%;
                  height: 12px;
                  margin-top: 18px;
                  accent-color: #77a5ff;
                }
                #status {
                  margin-top: 12px;
                  font-size: 14px;
                  white-space: pre-wrap;
                  overflow-wrap: anywhere;
                  color: #b8c9ea;
                }
                .footer {
                  margin-top: 22px;
                  font-size: 12px;
                  text-align: center;
                  color: #76849f;
                }
              </style>
            </head>
            <body>
              <main>
                <h1>Slipstream</h1>
                <p class="subtitle">Send files across your local network.</p>

                <div class="dropzone" id="dropzone">
                  <strong>파일 선택</strong>
                  <span>탭하거나 파일을 끌어다 놓으세요.</span>
                </div>

                <input id="file" type="file" multiple hidden>

                <button id="upload" disabled>전송하기</button>

                <progress id="progress" max="100" value="0"></progress>
                <div id="status">파일을 선택해 주세요.</div>

                <div class="footer">
                  Slipstream v0.1 · Local Transfer
                </div>
              </main>

              <script>
                const token = new URLSearchParams(
                  location.search
                ).get('token');

                const input = document.getElementById('file');
                const dropzone = document.getElementById('dropzone');
                const button = document.getElementById('upload');
                const progress = document.getElementById('progress');
                const status = document.getElementById('status');

                let selectedFiles = [];
                let uploading = false;

                dropzone.onclick = () => {
                  if (!uploading) input.click();
                };

                input.onchange = () => {
                  selectFiles(input.files);
                };

                function selectFiles(files) {
                  if (uploading) return;

                  selectedFiles = Array.from(files);
                  button.disabled = selectedFiles.length === 0;
                  progress.value = 0;

                  status.textContent = selectedFiles.length
                    ? selectedFiles.length + '개 파일 선택됨'
                    : '파일을 선택해 주세요.';
                }

                dropzone.ondragover = event => {
                  event.preventDefault();
                  dropzone.classList.add('active');
                };

                dropzone.ondragleave = () => {
                  dropzone.classList.remove('active');
                };

                dropzone.ondrop = event => {
                  event.preventDefault();
                  dropzone.classList.remove('active');
                  selectFiles(event.dataTransfer.files);
                };

                button.onclick = async () => {
                  if (uploading || selectedFiles.length === 0) return;

                  uploading = true;
                  button.disabled = true;

                  let success = 0;

                  for (let i = 0; i < selectedFiles.length; i++) {
                    const file = selectedFiles[i];

                    status.textContent =
                      `전송 중 (${i + 1}/${selectedFiles.length})\\n`
                      + file.name;

                    progress.value = 0;

                    try {
                      await sendFile(file);
                      success++;
                    } catch (error) {
                      status.textContent =
                        '전송 실패: ' + file.name
                        + '\\n' + error.message;

                      // Stop after an error.
                      break;
                    }
                  }

                  if (success === selectedFiles.length) {
                    progress.value = 100;
                    status.textContent =
                      `전송 완료! ${success}개 파일 저장됨.`;
                  }

                  uploading = false;
                  button.disabled = false;
                };

                function sendFile(file) {
                  return new Promise((resolve, reject) => {
                    const xhr = new XMLHttpRequest();

                    xhr.open('POST', '/api/upload');

                    xhr.setRequestHeader(
                      'X-Slipstream-Token', token
                    );

                    xhr.setRequestHeader(
                      'X-File-Name',
                      encodeURIComponent(file.name)
                    );

                    xhr.upload.onprogress = event => {
                      if (event.lengthComputable) {
                        progress.value = Math.round(
                          event.loaded / event.total * 100
                        );
                      }
                    };

                    xhr.onload = () => {
                      if (xhr.status === 200) {
                        resolve(xhr.responseText);
                      } else {
                        reject(new Error(
                          xhr.responseText || `HTTP ${xhr.status}`
                        ));
                      }
                    };

                    xhr.onerror = () => {
                      reject(new Error('네트워크 연결 실패'));
                    };

                    xhr.send(file);
                  });
                }
              </script>
            </body>
            </html>
            """;

}
