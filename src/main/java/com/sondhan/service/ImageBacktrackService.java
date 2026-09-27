package com.sondhan.service;

import com.sondhan.model.ImageBacktrackResult;
import com.sondhan.model.ImageBacktrackResult.Match;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;

/**
 * Image Backtracking Service.
 *
 * Strategy:
 *  1. Upload the image to Google Reverse Image Search using a multipart/form-data
 *     POST (the same request a browser makes at images.google.com).
 *  2. Parse the HTML response to extract matched page titles, URLs, and source domains.
 *  3. Send the image + discovered URLs to Claude AI, asking it to reason about
 *     the most likely original upload source and date context.
 *
 * No paid API keys required for the reverse-search step (uses the public Google endpoint).
 * Claude API key is only needed for the AI summary step.
 */
public class ImageBacktrackService {

    private static final String GOOGLE_LENS_URL =
        "https://lens.google.com/upload?ep=ccm&s=csp&st=" + System.currentTimeMillis();

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    // ── Public entry point ───────────────────────────────────────────────────

    /**
     * Perform a full image backtrack.
     * @param apiKey   Anthropic API key (may be null — Claude step is skipped)
     * @param imageFile the image file to backtrack
     */
    public static ImageBacktrackResult backtrack(String apiKey, File imageFile) throws Exception {
        // Step 1: Google Lens reverse image search
        List<Match> matches = googleLensSearch(imageFile);

        // Step 2: Claude AI origin analysis (if API key available)
        String summary;
        String earliest;
        if (apiKey != null && !apiKey.isBlank()) {
            summary  = claudeOriginAnalysis(apiKey, imageFile, matches);
            earliest = extractEarliestFromSummary(summary, matches);
        } else {
            summary  = buildFallbackSummary(matches);
            earliest = matches.isEmpty() ? "Unknown" : matches.get(0).source;
        }

        ImageBacktrackResult result = new ImageBacktrackResult();
        result.setMatches(matches);
        result.setSummary(summary);
        result.setEarliestSource(earliest);
        result.setOriginFound(!matches.isEmpty());
        return result;
    }

    // ── Step 1: Google Lens multipart upload ─────────────────────────────────

    private static List<Match> googleLensSearch(File imageFile) throws Exception {
        byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
        String boundary   = "----SondhanBoundary" + UUID.randomUUID().toString().replace("-", "");
        String mimeType   = detectMime(imageFile.getName());

        byte[] body = buildMultipartBody(boundary, imageBytes, imageFile.getName(), mimeType);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(GOOGLE_LENS_URL))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .header("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        String html = resp.body();

        // If redirected, the response may be the final Lens results page
        return parseGoogleLensHtml(html);
    }

    private static byte[] buildMultipartBody(String boundary, byte[] imageBytes,
                                              String fileName, String mimeType) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String CRLF = "\r\n";
        // Image part
        out.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"encoded_image\"; filename=\"" + fileName + "\"" + CRLF)
                  .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + mimeType + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(imageBytes);
        out.write(CRLF.getBytes(StandardCharsets.UTF_8));
        // image_content part
        out.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"image_content\"" + CRLF + CRLF)
                  .getBytes(StandardCharsets.UTF_8));
        out.write(CRLF.getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /**
     * Parse Google Lens HTML for result URLs and titles.
     * Google Lens encodes results in JSON blobs inside <script> tags and in <a href> links.
     */
    private static List<Match> parseGoogleLensHtml(String html) {
        List<Match> matches = new ArrayList<>();
        if (html == null || html.isBlank()) return matches;

        // Try to extract JSON data blocks embedded in the page
        matches.addAll(extractJsonMatches(html));

        // Fallback: scan <a href> anchor links with visible text
        if (matches.isEmpty()) {
            matches.addAll(extractAnchorMatches(html));
        }

        // Deduplicate by URL
        LinkedHashMap<String, Match> deduped = new LinkedHashMap<>();
        for (Match m : matches) deduped.putIfAbsent(m.url, m);

        return new ArrayList<>(deduped.values()).subList(0, Math.min(8, deduped.size()));
    }

    /** Extract from AF_initDataCallback JSON blobs that Google Lens embeds. */
    private static List<Match> extractJsonMatches(String html) {
        List<Match> out = new ArrayList<>();
        // Google Lens wraps result data in JS: AF_initDataCallback({key:'...', data: [...]})
        Pattern jsonBlock = Pattern.compile("AF_initDataCallback\\(\\{[^}]*data:(\\[.*?\\])\\s*\\}\\)",
                Pattern.DOTALL);
        Matcher m = jsonBlock.matcher(html);
        while (m.find() && out.size() < 10) {
            try {
                JSONArray arr = new JSONArray(m.group(1));
                extractFromJsonArray(arr, out);
            } catch (Exception ignored) {}
        }

        // Also look for _setWindow data patterns
        Pattern setWindow = Pattern.compile("window\\.jsl\\.dh\\('([^']+)'", Pattern.DOTALL);
        Matcher sm = setWindow.matcher(html);
        while (sm.find()) {
            try {
                String decoded = sm.group(1)
                    .replace("\\x22", "\"").replace("\\x27","'")
                    .replace("\\x3c","<").replace("\\x3e",">")
                    .replace("\\x26","&").replace("\\/","/");
                extractUrlsFromText(decoded, out);
            } catch (Exception ignored) {}
        }

        return out;
    }

    private static void extractFromJsonArray(JSONArray arr, List<Match> out) {
        for (int i = 0; i < arr.length() && out.size() < 10; i++) {
            try {
                Object elem = arr.get(i);
                if (elem instanceof JSONArray) extractFromJsonArray((JSONArray) elem, out);
                else if (elem instanceof String) {
                    String s = (String) elem;
                    if (s.startsWith("http") && !s.contains("google.com") && !s.contains("gstatic")) {
                        String domain = extractDomain(s);
                        out.add(new Match(domain, s, domain, "Found via reverse image search"));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static void extractUrlsFromText(String text, List<Match> out) {
        Pattern urlPat = Pattern.compile("https?://(?!(?:www\\.google|gstatic|googleapis))[^\\s\"'<>]+");
        Matcher m = urlPat.matcher(text);
        while (m.find() && out.size() < 10) {
            String url = m.group().replaceAll("[,;.!?)]+$","");
            if (url.length() > 15) {
                String domain = extractDomain(url);
                out.add(new Match(domain, url, domain, "Found via reverse image search"));
            }
        }
    }

    private static List<Match> extractAnchorMatches(String html) {
        List<Match> out = new ArrayList<>();
        Pattern anchor = Pattern.compile(
            "<a\\s[^>]*href=[\"'](https?://(?!(?:www\\.google|accounts\\.google|" +
            "support\\.google|play\\.google|gstatic|googleapis))[^\"'#?][^\"']*)[\"'][^>]*>" +
            "([^<]{3,120})</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = anchor.matcher(html);
        Set<String> seen = new HashSet<>();
        while (m.find() && out.size() < 8) {
            String url   = m.group(1).trim();
            String title = m.group(2).trim().replaceAll("<[^>]+>","").replaceAll("\\s+"," ");
            if (seen.add(url) && title.length() > 3) {
                String domain = extractDomain(url);
                out.add(new Match(title, url, domain, "Found via reverse image search"));
            }
        }
        return out;
    }

    // ── Step 2: Claude AI origin analysis ────────────────────────────────────

    private static String claudeOriginAnalysis(String apiKey, File imageFile,
                                                List<Match> matches) throws Exception {
        byte[]       bytes = Files.readAllBytes(imageFile.toPath());
        String       b64   = Base64.getEncoder().encodeToString(bytes);
        String       mime  = detectMime(imageFile.getName());

        StringBuilder urlList = new StringBuilder();
        for (int i = 0; i < matches.size(); i++) {
            Match match = matches.get(i);
            urlList.append(i + 1).append(". ").append(match.source)
                   .append(" — ").append(match.url).append("\n");
        }

        String prompt = "You are an image origin investigator. I am showing you an image.\n" +
            "Google Reverse Image Search found the following matching pages:\n" +
            (urlList.isEmpty() ? "(No matches found)\n" : urlList.toString()) + "\n" +
            "Based on the image content and the matched pages above, please:\n" +
            "1. Describe what this image shows.\n" +
            "2. Identify the most likely original source/website where this image was first uploaded.\n" +
            "3. Estimate when it may have first appeared online (if determinable from context).\n" +
            "4. Note if this image appears to be from news, social media, stock photos, or other origin.\n" +
            "5. Flag any signs of manipulation or reuse across multiple contexts.\n" +
            "Be concise. Use numbered points.";

        JSONObject imageContent = new JSONObject()
            .put("type", "image")
            .put("source", new JSONObject()
                .put("type", "base64")
                .put("media_type", mime)
                .put("data", b64));

        JSONObject textContent = new JSONObject()
            .put("type", "text")
            .put("text", prompt);

        JSONObject body = new JSONObject()
            .put("model", "claude-sonnet-4-5")
            .put("max_tokens", 1024)
            .put("system", "You are an image provenance and fact-checking expert.")
            .put("messages", new JSONArray().put(
                new JSONObject()
                    .put("role", "user")
                    .put("content", new JSONArray().put(imageContent).put(textContent))));

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.anthropic.com/v1/messages"))
            .header("Content-Type",        "application/json")
            .header("x-api-key",           apiKey)
            .header("anthropic-version",   "2023-06-01")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new RuntimeException("Claude API error " + resp.statusCode() + ": " + resp.body());

        JSONObject envelope = new JSONObject(resp.body());
        JSONArray  content  = envelope.getJSONArray("content");
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.getJSONObject(i);
            if ("text".equals(block.optString("type"))) return block.getString("text").trim();
        }
        return "Unable to generate origin analysis.";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String extractEarliestFromSummary(String summary, List<Match> matches) {
        if (summary == null || summary.isBlank()) {
            return matches.isEmpty() ? "Unknown" : matches.get(0).source;
        }
        // Look for a domain name or "original source" mention in the summary
        for (Match m : matches) {
            if (summary.contains(m.source)) return m.source;
        }
        return matches.isEmpty() ? "Unknown" : matches.get(0).source;
    }

    private static String buildFallbackSummary(List<Match> matches) {
        if (matches.isEmpty()) {
            return "No matching pages were found for this image via reverse image search.\n" +
                   "The image may be original, very recent, or not indexed by search engines.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Reverse image search found ").append(matches.size()).append(" matching page(s):\n\n");
        for (int i = 0; i < matches.size(); i++) {
            Match m = matches.get(i);
            sb.append(i + 1).append(". ").append(m.title).append("\n   ").append(m.url).append("\n\n");
        }
        sb.append("Add an Anthropic API key to get a detailed AI-powered origin analysis.");
        return sb.toString();
    }

    private static String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) return url;
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return url.length() > 40 ? url.substring(0, 40) + "…" : url;
        }
    }

    private static String detectMime(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png"))  return "image/png";
        if (n.endsWith(".gif"))  return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
