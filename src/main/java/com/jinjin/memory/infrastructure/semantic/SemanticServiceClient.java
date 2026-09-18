package com.jinjin.memory.infrastructure.semantic;

import com.jinjin.memory.infrastructure.util.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 调用 Python 语义侧车 {@code semantic-service}（默认 http://127.0.0.1:8710）。
 */
public class SemanticServiceClient {
    private static final Logger LOGGER = Logger.getLogger(SemanticServiceClient.class.getName());
    private static final Pattern LAYER_HINT = Pattern.compile(
            "\"layer\":\"(SCHEMA|THEME|CONCEPT)\",\"label\":\"((?:\\\\.|[^\"])*)\",\"confidence\":([0-9.]+)"
    );
    private static final Pattern ENTITY = Pattern.compile(
            "\"name\":\"((?:\\\\.|[^\"])*)\",\"type\":\"([A-Z_]+)\",\"confidence\":([0-9.]+)"
    );
    private static final Pattern EMBED_ITEM = Pattern.compile(
            "\"id\":\"((?:\\\\.|[^\"])*)\",\"text\":\"(?:\\\\.|[^\"])*\",\"embedding\":\\[([0-9eE+\\-. ,]+)\\]"
    );

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration timeout;
    private volatile boolean available = true;

    public SemanticServiceClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = Duration.ofSeconds(60);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public static Optional<SemanticServiceClient> fromEnvironment() {
        String url = System.getenv("JINJIN_SEMANTIC_URL");
        if (url == null || url.isBlank()) {
            url = "http://127.0.0.1:8710";
        }
        if ("off".equalsIgnoreCase(url) || "false".equalsIgnoreCase(url)) {
            return Optional.empty();
        }
        return Optional.of(new SemanticServiceClient(url));
    }

    public boolean isEnabled() {
        return available;
    }

    public Optional<SemanticAnalysis> analyze(String text, String memoryId) {
        if (!available) {
            return Optional.empty();
        }
        StringBuilder body = new StringBuilder("{\"text\":\"").append(Json.escape(text)).append("\"");
        if (memoryId != null && !memoryId.isBlank()) {
            body.append(",\"memory_id\":\"").append(Json.escape(memoryId)).append("\"");
        }
        body.append("}");
        return post("/api/v1/analyze", body.toString()).map(this::parseAnalyze);
    }

    public Optional<SemanticUnderstanding> understand(String query, int limit) {
        if (!available) {
            return Optional.empty();
        }
        String body = "{\"query\":\"" + Json.escape(query) + "\",\"limit\":" + limit + "}";
        return post("/api/v1/understand", body).map(this::parseUnderstand);
    }

    public Optional<List<EmbedItem>> embed(List<String> texts, List<String> ids) {
        if (!available || texts.isEmpty()) {
            return Optional.empty();
        }
        if (ids != null && ids.size() != texts.size()) {
            throw new IllegalArgumentException("ids size must match texts size");
        }

        StringBuilder body = new StringBuilder("{\"texts\":");
        body.append(Json.writeArray(texts));
        if (ids != null) {
            body.append(",\"ids\":").append(Json.writeArray(ids));
        }
        body.append("}");
        return post("/api/v1/embed", body.toString()).map(this::parseEmbed);
    }

    public boolean ping() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            available = response.statusCode() == 200;
            return available;
        } catch (Exception e) {
            available = false;
            return false;
        }
    }

    private Optional<String> post(String path, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                LOGGER.log(Level.WARNING, "Semantic service {0} returned {1}", new Object[]{path, response.statusCode()});
                return Optional.empty();
            }
            return Optional.of(response.body());
        } catch (Exception e) {
            available = false;
            LOGGER.log(Level.FINE, "Semantic service unavailable at {0}{1}: {2}",
                    new Object[]{baseUrl, path, e.getMessage()});
            return Optional.empty();
        }
    }

    private SemanticAnalysis parseAnalyze(String json) {
        List<LayerHint> layerHints = parseLayerHints(json, "layer_hints");
        List<String> entities = parseEntityNames(json);
        return new SemanticAnalysis(
                Json.readString(json, "title_hint"),
                Json.readArray(json, "tags"),
                entities,
                layerHints,
                Json.readArray(json, "keywords"),
                Json.readNumberArray(json, "embedding"),
                Json.readString(json, "embedding_model"),
                Json.readInt(json, "embedding_dim", 0)
        );
    }

    private List<EmbedItem> parseEmbed(String json) {
        List<EmbedItem> items = new ArrayList<>();
        Matcher matcher = EMBED_ITEM.matcher(json);
        while (matcher.find()) {
            items.add(new EmbedItem(unescape(matcher.group(1)), parseNumberList(matcher.group(2))));
        }
        return items;
    }

    private List<Double> parseNumberList(String raw) {
        List<Double> values = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(Double.parseDouble(trimmed));
            }
        }
        return values;
    }

    private SemanticUnderstanding parseUnderstand(String json) {
        return new SemanticUnderstanding(
                Json.readString(json, "intent"),
                parseLayerHints(json, "schema_hints"),
                parseLayerHints(json, "theme_hints"),
                parseLayerHints(json, "concept_hints"),
                Json.readArray(json, "keywords"),
                Json.readNumberArray(json, "query_embedding"),
                Json.readString(json, "embedding_model"),
                Json.readInt(json, "embedding_dim", 0)
        );
    }

    private List<LayerHint> parseLayerHints(String json, String key) {
        String marker = "\"" + key + "\":[";
        int start = json.indexOf(marker);
        if (start < 0) {
            return List.of();
        }
        int end = json.indexOf(']', start);
        if (end < 0) {
            return List.of();
        }
        String slice = json.substring(start, end);
        List<LayerHint> hints = new ArrayList<>();
        Matcher matcher = LAYER_HINT.matcher(slice);
        while (matcher.find()) {
            hints.add(new LayerHint(
                    matcher.group(1),
                    unescape(matcher.group(2)),
                    Double.parseDouble(matcher.group(3)),
                    ""
            ));
        }
        return hints;
    }

    private List<String> parseEntityNames(String json) {
        String marker = "\"entities\":[";
        int start = json.indexOf(marker);
        if (start < 0) {
            return List.of();
        }
        int end = json.indexOf(']', start);
        if (end < 0) {
            return List.of();
        }
        String slice = json.substring(start, end);
        List<String> names = new ArrayList<>();
        Matcher matcher = ENTITY.matcher(slice);
        while (matcher.find()) {
            names.add(unescape(matcher.group(1)));
        }
        return names;
    }

    private String unescape(String value) {
        return value.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n");
    }
}
