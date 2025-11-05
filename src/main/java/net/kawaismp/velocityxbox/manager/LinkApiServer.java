package net.kawaismp.velocityxbox.manager;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.kawaismp.velocityxbox.VelocityXbox;
import net.kawaismp.velocityxbox.database.model.Account;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class LinkApiServer {
    private final VelocityXbox plugin;
    private final Logger logger;
    private final Gson gson;
    private HttpServer server;
    private final String secretKey;

    public LinkApiServer(VelocityXbox plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.gson = new Gson();
        this.secretKey = plugin.getConfigManager().getLinkApiSecret();
    }

    /**
     * Start the HTTP server
     */
    public void start() {
        try {
            int port = plugin.getConfigManager().getLinkApiPort();
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            server.createContext("/link", new LinkHandler());
            server.setExecutor(null); // Use default executor
            server.start();

            logger.info("Link API server started on port {}", port);
            logger.info("Link API endpoint: http://localhost:{}/link", port);
        } catch (IOException e) {
            logger.error("Failed to start Link API server", e);
            throw new RuntimeException("Failed to start Link API server", e);
        }
    }

    /**
     * Stop the HTTP server
     */
    public void stop() {
        if (server != null) {
            logger.info("Stopping Link API server...");
            server.stop(0);
        }
    }

    /**
     * HTTP handler for /link endpoint
     */
    private class LinkHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            // Only accept GET requests
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, createErrorResponse("Method not allowed. Use GET."));
                return;
            }

            try {
                // Parse query parameters
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());

                // Validate secret key
                String providedSecret = params.get("secret_key");
                if (providedSecret == null || !providedSecret.equals(secretKey)) {
                    logger.warn("Unauthorized link API request from {}", exchange.getRemoteAddress());
                    sendResponse(exchange, 401, createErrorResponse("Unauthorized: Invalid secret key"));
                    return;
                }

                // Get required parameters
                String code = params.get("code");
                String discordId = params.get("discord_id");

                if (code == null || code.isEmpty()) {
                    sendResponse(exchange, 400, createErrorResponse("Missing required parameter: code"));
                    return;
                }

                if (discordId == null || discordId.isEmpty()) {
                    sendResponse(exchange, 400, createErrorResponse("Missing required parameter: discord_id"));
                    return;
                }

                // Process the link request asynchronously
                processLinkRequest(exchange, code, discordId);
            } catch (Exception e) {
                logger.error("Error handling link request", e);
                sendResponse(exchange, 500, createErrorResponse("Internal server error"));
            }
        }

        private void processLinkRequest(HttpExchange exchange, String code, String discordId) {
            // Verify the code
            Optional<String> usernameOpt = plugin.getLinkCodeManager().verifyAndConsume(code);

            if (usernameOpt.isEmpty()) {
                sendResponse(exchange, 404, createErrorResponse("Invalid or expired code. Generate a new one with /link"));
                return;
            }

            String username = usernameOpt.get();
            logger.debug("Processing Discord link request: code={}, username={}, discordId={}", code, username, discordId);

            // Get the account from database
            CompletableFuture<Optional<Account>> accountFuture = plugin.getDatabaseManager().getAccountByUsername(username);

            accountFuture.thenAccept(accountOpt -> {
                if (accountOpt.isEmpty()) {
                    logger.debug("Account not found for username: {}", username);
                    sendResponse(exchange, 404, createErrorResponse("Account not found"));
                    return;
                }

                Account account = accountOpt.get();

                // Link the Discord account
                plugin.getDatabaseManager().linkDiscordAccount(account.id(), discordId)
                    .thenAccept(success -> {
                        if (success) {
                            logger.info("Successfully linked Discord account {} to {}", discordId, username);

                            // Create success response
                            JsonObject response = new JsonObject();
                            response.addProperty("success", true);
                            response.addProperty("minecraft_username", username);
                            response.addProperty("message", "Successfully linked!");

                            sendResponse(exchange, 200, response);
                        } else {
                            logger.error("Failed to link Discord account {} to {}", discordId, username);
                            sendResponse(exchange, 500, createErrorResponse("Failed to link account. Please try again."));
                        }
                    })
                    .exceptionally(throwable -> {
                        logger.debug("Error linking Discord account", throwable);
                        sendResponse(exchange, 500, createErrorResponse("Internal server error"));
                        return null;
                    });
            }).exceptionally(throwable -> {
                logger.debug("Error fetching account", throwable);
                sendResponse(exchange, 500, createErrorResponse("Internal server error"));
                return null;
            });
        }

        private Map<String, String> parseQueryParams(String query) {
            Map<String, String> params = new HashMap<>();
            if (query == null || query.isEmpty()) {
                return params;
            }

            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                    params.put(key, value);
                }
            }
            return params;
        }

        private JsonObject createErrorResponse(String message) {
            JsonObject response = new JsonObject();
            response.addProperty("success", false);
            response.addProperty("message", message);
            return response;
        }

        private void sendResponse(HttpExchange exchange, int statusCode, JsonObject response) {
            try (exchange) {
                String jsonResponse = gson.toJson(response);
                byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(statusCode, bytes.length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (IOException e) {
                logger.error("Error sending response", e);
            }
        }
    }
}

