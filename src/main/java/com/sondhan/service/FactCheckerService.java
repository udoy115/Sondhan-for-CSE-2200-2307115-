package com.sondhan.service;
import com.sondhan.model.FactCheckResult;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Topic 2: All API calls run on ExecutorService background threads - UI never freezes.
 * Topic 4: Uses org.json to build JSON requests and parse JSON responses from Claude API.
 */
public class FactCheckerService {
    private static final String API_URL   = "https://api.anthropic.com/v1/messages";
    private static final String API_VER   = "2023-06-01";
    private static final String MODEL     = "claude-sonnet-4-5";
    private static final int    MAX_TOKENS = 2048;

    /** Topic 2: Fixed thread pool - daemon threads so they don't block JVM exit. */
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "sondhan-api-thread");
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30)).build();

    public static ExecutorService getExecutor() { return EXECUTOR; }

    private static final String SYS = "You are Sondhani, a professional fact-checking AI. " +
        "Verify claims thoroughly. Be neutral. " +
        "Respond ONLY with valid JSON - no extra text, no markdown fences.";

    /** Topic 4: Build JSON request body, POST to Claude API, parse JSON response. */
    public static FactCheckResult checkTextClaim(String apiKey, String claim) throws Exception {
        String prompt = "Fact-check this claim: \"" + claim + "\"\n\n" +
            "Return ONLY this JSON:\n" +
            "{\"verdict\":\"TRUE|FALSE|MISLEADING|UNVERIFIED\"," +
            "\"confidence\":<50-99>," +
            "\"explanation\":\"<2-3 sentences>\"," +
            "\"sources\":[{\"title\":\"<name>\",\"url\":\"<url>\"}]," +
            "\"claim\":\"<restated claim>\"}";

        JSONObject body = new JSONObject()
            .put("model", MODEL).put("max_tokens", MAX_TOKENS).put("system", SYS)
            .put("messages", new JSONArray().put(
                new JSONObject().put("role","user").put("content", prompt)));

        return parseResponse(post(apiKey, body.toString()), claim);
    }

    /** Topic 4: Encodes image as Base64, embeds in JSON, sends to Claude vision API. */
    public static FactCheckResult checkImageClaim(String apiKey, File imageFile) throws Exception {
        byte[] bytes = Files.readAllBytes(imageFile.toPath());
        String b64   = Base64.getEncoder().encodeToString(bytes);
        String mime  = detectMime(imageFile.getName());

        String prompt = "Extract the main claim from this image and fact-check it.\n" +
            "Return ONLY this JSON:\n" +
            "{\"verdict\":\"TRUE|FALSE|MISLEADING|UNVERIFIED\"," +
            "\"confidence\":<50-99>," +
            "\"explanation\":\"<2-3 sentences>\"," +
            "\"sources\":[{\"title\":\"<name>\",\"url\":\"<url>\"}]," +
            "\"claim\":\"<extracted claim>\"}";

        JSONObject body = new JSONObject()
            .put("model", MODEL).put("max_tokens", MAX_TOKENS).put("system", SYS)
            .put("messages", new JSONArray().put(new JSONObject()
                .put("role","user")
                .put("content", new JSONArray()
                    .put(new JSONObject().put("type","image").put("source",
                        new JSONObject().put("type","base64").put("media_type",mime).put("data",b64)))
                    .put(new JSONObject().put("type","text").put("text",prompt)))));

        return parseResponse(post(apiKey, body.toString()), null);
    }

    public static String generateSummary(String apiKey, String claim, String verdict,
                                          String explanation, List<FactCheckResult.Source> sources)
            throws Exception {
        String src = sources == null ? "" : sources.stream()
            .map(s -> s.title + " (" + s.url + ")").reduce("", (a,b) -> a + "\n" + b);
        String prompt = "Write exactly 10 numbered sentences summarising:\nClaim: " + claim +
            "\nVerdict: " + verdict + "\nExplanation: " + explanation + "\nSources: " + src;
        JSONObject body = new JSONObject().put("model",MODEL).put("max_tokens",1024)
            .put("messages", new JSONArray().put(
                new JSONObject().put("role","user").put("content",prompt)));
        JSONObject resp = new JSONObject(post(apiKey, body.toString()));
        JSONArray content = resp.getJSONArray("content");
        for (int i=0; i<content.length(); i++) {
            JSONObject block = content.getJSONObject(i);
            if ("text".equals(block.optString("type"))) return block.getString("text").trim();
        }
        return "Summary unavailable.";
    }

    /** Topic 4: HTTP POST to Anthropic API. Called from background thread (Topic 2). */
    private static String post(String apiKey, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Content-Type","application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", API_VER)
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new RuntimeException("API error " + resp.statusCode() + ": " + resp.body());
        return resp.body();
    }

    /** Topic 4: Parse Anthropic JSON envelope, extract text block, parse inner JSON payload. */
    private static FactCheckResult parseResponse(String raw, String fallback) throws Exception {
        JSONObject envelope = new JSONObject(raw);
        JSONArray  content  = envelope.getJSONArray("content");
        String text = "";
        for (int i=0; i<content.length(); i++) {
            JSONObject b = content.getJSONObject(i);
            if ("text".equals(b.optString("type"))) { text = b.getString("text"); break; }
        }
        // Strip markdown fences
        String cleaned = text.trim()
            .replaceAll("(?i)^`json\\s*","").replaceAll("\\s*`$","").trim();
        int s = cleaned.indexOf('{'), e = cleaned.lastIndexOf('}');
        if (s >= 0 && e > s) cleaned = cleaned.substring(s, e+1);

        FactCheckResult r = new FactCheckResult();
        try {
            JSONObject j = new JSONObject(cleaned);
            r.setClaim(j.optString("claim", fallback != null ? fallback : "Unknown claim"));
            r.setVerdict(j.optString("verdict","UNVERIFIED").toUpperCase());
            r.setConfidence(j.optInt("confidence",50));
            r.setExplanation(j.optString("explanation", text));
            List<FactCheckResult.Source> sources = new ArrayList<>();
            JSONArray arr = j.optJSONArray("sources");
            if (arr != null) for (int i=0; i<arr.length(); i++) {
                JSONObject src = arr.getJSONObject(i);
                sources.add(new FactCheckResult.Source(src.optString("title","Source"),src.optString("url","#")));
            }
            if (sources.isEmpty()) sources.add(new FactCheckResult.Source("Claude AI Analysis","https://anthropic.com"));
            r.setSources(sources);
        } catch (Exception ex) {
            r.setClaim(fallback != null ? fallback : "Unknown"); r.setVerdict("UNVERIFIED");
            r.setConfidence(50); r.setExplanation(text.isEmpty() ? "Could not parse response." : text);
            r.setSources(List.of(new FactCheckResult.Source("Claude AI","https://anthropic.com")));
        }
        return r;
    }

    private static String detectMime(String name) {
        name = name.toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}