package com.globalconnectivity.nbt.net;

import com.globalconnectivity.nbt.NbtFileStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** HTTP client for the companion C# server (see /csharp in the repo). */
public final class CloudApi {
    public record CloudFile(String name, long sizeBytes) {}

    public static final class ApiException extends RuntimeException {
        public final int status;

        public ApiException(String message) {
            super(message);
            this.status = 0;
        }

        public ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private CloudApi() {}

    public static String normalizeBaseUrl(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("empty server address");
        }
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "http://" + s;
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static CompletableFuture<List<CloudFile>> listFiles(String baseUrl) {
        return CompletableFuture.supplyAsync(() -> {
            HttpRequest req = HttpRequest.newBuilder(URI.create(normalizeBaseUrl(baseUrl) + "/api/nbt/list"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = send(req, HttpResponse.BodyHandlers.ofString());
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonArray arr = root.has("files") && root.get("files").isJsonArray() ? root.getAsJsonArray("files") : new JsonArray();
            List<CloudFile> out = new ArrayList<>();
            for (var e : arr) {
                JsonObject o = e.getAsJsonObject();
                out.add(new CloudFile(
                        o.has("name") ? o.get("name").getAsString() : "?",
                        o.has("sizeBytes") ? o.get("sizeBytes").getAsLong() : 0L));
            }
            return out;
        });
    }

    public static CompletableFuture<Void> uploadFile(String baseUrl, String name, Path file) {
        return CompletableFuture.supplyAsync(() -> {
            byte[] data;
            try {
                data = Files.readAllBytes(file);
            } catch (IOException e) {
                throw new RuntimeException("read local file failed: " + e.getMessage(), e);
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(normalizeBaseUrl(baseUrl) + "/api/nbt/upload?name=" + enc(name)))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(data))
                    .build();
            send(req, HttpResponse.BodyHandlers.ofString());
            return null;
        });
    }

    public static CompletableFuture<Path> downloadFile(String baseUrl, String name, String targetName) {
        return CompletableFuture.supplyAsync(() -> {
            HttpRequest req = HttpRequest.newBuilder(URI.create(normalizeBaseUrl(baseUrl) + "/api/nbt/download/" + enc(name)))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            try {
                Path folder = NbtFileStore.folder();
                Files.createDirectories(folder);
                Path target = folder.resolve(NbtFileStore.sanitizeName(targetName));
                Path tmp = folder.resolve(target.getFileName().toString() + ".part");
                Path body = HTTP.send(req, HttpResponse.BodyHandlers.ofFile(tmp)).body();
                Files.move(body, target, StandardCopyOption.REPLACE_EXISTING);
                return target;
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted", e);
            }
        });
    }

    public static CompletableFuture<String> ping(String baseUrl) {
        return CompletableFuture.supplyAsync(() -> {
            HttpRequest req = HttpRequest.newBuilder(URI.create(normalizeBaseUrl(baseUrl) + "/api/health"))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> resp = send(req, HttpResponse.BodyHandlers.ofString());
            JsonObject o = JsonParser.parseString(resp.body()).getAsJsonObject();
            String status = o.has("status") ? o.get("status").getAsString() : "?";
            String version = o.has("version") ? o.get("version").getAsString() : "?";
            long files = o.has("files") && o.get("files").isJsonPrimitive() ? o.get("files").getAsLong() : 0L;
            return status + ", v" + version + ", " + files + " files";
        });
    }

    private static <T> HttpResponse<T> send(HttpRequest req, HttpResponse.BodyHandler<T> handler) {
        try {
            HttpResponse<T> resp = HTTP.send(req, handler);
            if (resp.statusCode() / 100 != 2) {
                String snippet = "";
                if (resp.body() instanceof String s) {
                    snippet = s == null ? "" : s.substring(0, Math.min(s.length(), 120));
                }
                throw new ApiException(resp.statusCode(), "HTTP " + resp.statusCode() + (snippet.isEmpty() ? "" : " - " + snippet));
            }
            return resp;
        } catch (IOException e) {
            throw new ApiException("无法连接服务器: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("请求被中断");
        }
    }
}
