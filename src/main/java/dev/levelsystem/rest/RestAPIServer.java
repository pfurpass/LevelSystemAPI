package dev.levelsystem.rest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.levelsystem.api.LevelAPI;
import dev.levelsystem.api.LevelPlayer;
import dev.levelsystem.api.Skill;
import dev.levelsystem.api.XPSource;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Lightweight REST API for LevelSystemAPI.
 *
 * <h3>Endpoints:</h3>
 * <pre>
 *   GET  /player/{uuid}              → full player data (level, xp, skills)
 *   GET  /player/{uuid}/level        → current level
 *   GET  /player/{uuid}/xp           → current xp
 *   GET  /player/{uuid}/xp_required  → xp needed for next level
 *   POST /player/{uuid}/xp/add?amount=100&source=ADMIN
 *   POST /player/{uuid}/level/set?level=10
 *   GET  /skills                     → list of registered skills
 *   GET  /health                     → API health check
 * </pre>
 *
 * All endpoints require the header: {@code X-Api-Key: <secret-key>}
 */
public class RestAPIServer {

    private final LevelAPI api;
    private final Logger log;
    private final String secretKey;
    private HttpServer server;

    public RestAPIServer(LevelAPI api, Logger log, String secretKey) {
        this.api = api;
        this.log = log;
        this.secretKey = secretKey;
    }

    public void start(String host, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));

        server.createContext("/health",          this::handleHealth);
        server.createContext("/skills",          this::handleSkills);
        server.createContext("/player/",         this::handlePlayer);

        server.start();
        log.info("[LevelSystem] REST API running on " + host + ":" + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            log.info("[LevelSystem] REST API stopped.");
        }
    }

    // ── Handlers ─────────────────────────────────────────────────────────

    private void handleHealth(HttpExchange ex) throws IOException {
        if (!authOk(ex)) return;
        sendJson(ex, 200, "{\"status\":\"ok\",\"players\":" + api.getStorage().isHealthy() + "}");
    }

    private void handleSkills(HttpExchange ex) throws IOException {
        if (!authOk(ex)) return;
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Skill s : api.getSkills()) {
            if (!first) sb.append(",");
            sb.append("{\"id\":\"").append(s.getId())
              .append("\",\"displayName\":\"").append(s.getDisplayName())
              .append("\",\"maxLevel\":").append(s.getMaxLevel()).append("}");
            first = false;
        }
        sb.append("]");
        sendJson(ex, 200, sb.toString());
    }

    private void handlePlayer(HttpExchange ex) throws IOException {
        if (!authOk(ex)) return;

        // Parse path: /player/{uuid}[/action]
        String path = ex.getRequestURI().getPath(); // e.g. /player/abc-123/level
        String[] parts = path.split("/");
        // parts[0]="" parts[1]="player" parts[2]=uuid parts[3]=sub?

        if (parts.length < 3) { sendError(ex, 400, "Missing UUID"); return; }

        UUID uuid;
        try { uuid = UUID.fromString(parts[2]); }
        catch (IllegalArgumentException e) { sendError(ex, 400, "Invalid UUID"); return; }

        String method = ex.getRequestMethod();
        String sub = parts.length >= 4 ? parts[3] : "";

        // POST routes
        if (method.equalsIgnoreCase("POST")) {
            String query = ex.getRequestURI().getQuery();

            if (sub.equals("xp") && parts.length >= 5 && parts[4].equals("add")) {
                long amount = queryParam(query, "amount", 0L);
                XPSource source = parseSource(queryParam(query, "source", "ADMIN"));
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    api.addXP(online, amount, source);
                    sendJson(ex, 200, "{\"success\":true,\"added\":" + amount + "}");
                } else {
                    sendError(ex, 404, "Player not online");
                }
                return;
            }

            if (sub.equals("level") && parts.length >= 5 && parts[4].equals("set")) {
                int level = (int) queryParam(query, "level", 0L);
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    api.setLevel(online, level);
                    sendJson(ex, 200, "{\"success\":true,\"level\":" + level + "}");
                } else {
                    sendError(ex, 404, "Player not online");
                }
                return;
            }

            sendError(ex, 404, "Unknown POST route");
            return;
        }

        // GET routes
        LevelPlayer lp = api.getPlayer(uuid);

        switch (sub) {
            case "level" -> sendJson(ex, 200, "{\"level\":" + lp.getLevel() + "}");
            case "xp"    -> sendJson(ex, 200, "{\"xp\":" + lp.getXP() + "}");
            case "xp_required" -> {
                long req = api.getLevelFormula().xpForLevel(lp.getLevel() + 1);
                sendJson(ex, 200, "{\"xp_required\":" + req + "}");
            }
            default -> {
                // Full player object
                StringBuilder sb = new StringBuilder();
                sb.append("{")
                  .append("\"uuid\":\"").append(uuid).append("\",")
                  .append("\"name\":\"").append(lp.getName()).append("\",")
                  .append("\"level\":").append(lp.getLevel()).append(",")
                  .append("\"xp\":").append(lp.getXP()).append(",")
                  .append("\"xp_required\":").append(api.getLevelFormula().xpForLevel(lp.getLevel() + 1)).append(",")
                  .append("\"skills\":{");
                boolean first = true;
                for (Skill s : api.getSkills()) {
                    if (!first) sb.append(",");
                    sb.append("\"").append(s.getId()).append("\":{")
                      .append("\"level\":").append(lp.getLevel(s)).append(",")
                      .append("\"xp\":").append(lp.getXP(s)).append("}");
                    first = false;
                }
                sb.append("}}");
                sendJson(ex, 200, sb.toString());
            }
        }
    }

    // ── Util ─────────────────────────────────────────────────────────────

    private boolean authOk(HttpExchange ex) throws IOException {
        String key = ex.getRequestHeaders().getFirst("X-Api-Key");
        if (secretKey != null && !secretKey.isEmpty() && !secretKey.equals(key)) {
            sendError(ex, 401, "Unauthorized");
            return false;
        }
        return true;
    }

    private void sendJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void sendError(HttpExchange ex, int status, String msg) throws IOException {
        sendJson(ex, status, "{\"error\":\"" + msg + "\"}");
    }

    private long queryParam(String query, String key, long def) {
        if (query == null) return def;
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                try { return Long.parseLong(kv[1]); } catch (NumberFormatException e) { return def; }
            }
        }
        return def;
    }

    private String queryParam(String query, String key, String def) {
        if (query == null) return def;
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) return kv[1];
        }
        return def;
    }

    private XPSource parseSource(String s) {
        try { return XPSource.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return XPSource.ADMIN; }
    }
}
