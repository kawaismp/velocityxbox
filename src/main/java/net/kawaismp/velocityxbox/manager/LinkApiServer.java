package net.kawaismp.velocityxbox.manager;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.kawaismp.velocityxbox.VelocityXbox;
import net.kawaismp.velocityxbox.database.model.Account;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class LinkApiServer {
    private static final int MAX_REQUESTS_PER_IP = 10; // Max requests per IP per minute
    private static final int MAX_PARAM_LENGTH = 100; // Max length for parameters
    private static final int MAX_ACCOUNTS_PER_DISCORD = 3; // Max accounts that can be linked to one Discord account
    private static final Pattern DISCORD_ID_PATTERN = Pattern.compile("^\\d{17,20}$"); // Valid Discord ID
    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$"); // 6-digit code
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$"); // Valid Minecraft username

    private final VelocityXbox plugin;
    private final Logger logger;
    private final Gson gson;
    private Server server;
    private final String secretKey;
    private final Map<String, RateLimitEntry> rateLimitMap;
    private final Map<String, Long> discordLinkAttempts; // Track Discord ID link attempts

    public LinkApiServer(VelocityXbox plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.gson = new Gson();
        this.secretKey = plugin.getConfigManager().getLinkApiSecret();
        this.rateLimitMap = new ConcurrentHashMap<>();
        this.discordLinkAttempts = new ConcurrentHashMap<>();

        // Validate secret key
        if (secretKey == null || secretKey.isEmpty() || secretKey.equals("change_me_to_a_secure_random_string")) {
            throw new IllegalStateException("Link API secret key must be configured! Please set a secure secret in config.yml");
        }
        if (secretKey.length() < 16) {
            logger.warn("Link API secret key is too short! Use at least 16 characters for better security.");
        }
    }

    /**
     * Start the HTTP server with Jetty for better performance
     */
    public void start() {
        try {
            int port = plugin.getConfigManager().getLinkApiPort();

            // Create a thread pool with limited size to prevent resource exhaustion
            // Jetty requires at least 3 threads (some are reserved for internal operations)
            QueuedThreadPool threadPool = new QueuedThreadPool();
            threadPool.setMaxThreads(4);  // Limit max threads
            threadPool.setMinThreads(3);   // Keep some threads ready
            threadPool.setIdleTimeout(30000); // 30 second idle timeout
            threadPool.setName("LinkAPI");

            server = new Server(threadPool);

            // Configure server connector
            ServerConnector connector = new ServerConnector(server);
            connector.setPort(port);
            connector.setHost("0.0.0.0");
            connector.setIdleTimeout(30000); // 30 second idle timeout
            connector.setAcceptQueueSize(20); // Limit pending connections
            server.addConnector(connector);

            // Setup servlet
            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
            context.setContextPath("/");
            server.setHandler(context);

            context.addServlet(new ServletHolder(new LinkServlet()), "/link");

            server.start();

            logger.info("Link API server (Jetty) started on port {}", port);
        } catch (Exception e) {
            logger.error("Failed to start Link API server", e);
            throw new RuntimeException("Failed to start Link API server", e);
        }
    }

    /**
     * Stop the HTTP server gracefully
     */
    public void stop() {
        if (server != null) {
            logger.info("Stopping Link API server...");
            try {
                server.stop();
                server.destroy();
            } catch (Exception e) {
                logger.error("Error stopping Link API server", e);
            }
        }
    }

    /**
     * Servlet handler for /link endpoint
     */
    private class LinkServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
            handleLinkRequest(req, resp);
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
            handleLinkRequest(req, resp);
        }

        private void handleLinkRequest(HttpServletRequest req, HttpServletResponse resp) {
            // Start async context for handling CompletableFuture operations
            jakarta.servlet.AsyncContext asyncContext = null;

            try {
                asyncContext = req.startAsync();
                asyncContext.setTimeout(30000); // 30 second timeout

                String clientIp = getClientIp(req);

                // Rate limiting check
                if (!checkRateLimit(clientIp)) {
                    logger.warn("Rate limit exceeded for IP: {}", clientIp);
                    sendJsonResponse(resp, 429, createErrorResponse("Too many requests. Please try again later."));
                    asyncContext.complete();
                    return;
                }

                // Parse query parameters
                Map<String, String> params = parseQueryParams(req.getQueryString());

                // Validate secret key with timing-attack protection
                String providedSecret = params.get("secret_key");
                if (!isValidSecretKey(providedSecret)) {
                    logger.warn("Unauthorized link API request from {}", clientIp);
                    sendJsonResponse(resp, 401, createErrorResponse("Unauthorized: Invalid secret key"));
                    asyncContext.complete();
                    return;
                }

                // Get and validate required parameters
                String code = params.get("code");
                String discordId = params.get("discord_id");

                if (code == null || code.isEmpty()) {
                    sendJsonResponse(resp, 400, createErrorResponse("Missing required parameter: code"));
                    asyncContext.complete();
                    return;
                }

                if (discordId == null || discordId.isEmpty()) {
                    sendJsonResponse(resp, 400, createErrorResponse("Missing required parameter: discord_id"));
                    asyncContext.complete();
                    return;
                }

                // Validate input format and length
                String validationError = validateInputs(code, discordId);
                if (validationError != null) {
                    logger.warn("Invalid input from {}: {}", clientIp, validationError);
                    sendJsonResponse(resp, 400, createErrorResponse(validationError));
                    asyncContext.complete();
                    return;
                }

                // Check Discord ID rate limiting (prevent one Discord account from spamming)
                if (!checkDiscordRateLimit(discordId)) {
                    logger.warn("Discord ID {} exceeded rate limit", discordId);
                    sendJsonResponse(resp, 429, createErrorResponse("Too many link attempts. Please wait before trying again."));
                    asyncContext.complete();
                    return;
                }

                // Process the link request asynchronously
                processLinkRequest(asyncContext, code, discordId, clientIp);
            } catch (Exception e) {
                logger.error("Error handling link request", e);
                try {
                    sendJsonResponse(resp, 500, createErrorResponse("Internal server error"));
                } catch (Exception ex) {
                    logger.error("Error sending error response", ex);
                }
                if (asyncContext != null) {
                    asyncContext.complete();
                }
            }
        }

        private void processLinkRequest(jakarta.servlet.AsyncContext asyncContext, String code, String discordId, String clientIp) {
            HttpServletResponse resp = (HttpServletResponse) asyncContext.getResponse();

            // Verify the code
            Optional<String> usernameOpt = plugin.getLinkCodeManager().verifyAndConsume(code);

            if (usernameOpt.isEmpty()) {
                try {
                    sendJsonResponse(resp, 404, createErrorResponse("Invalid or expired code. Generate a new one with /link"));
                } catch (IOException e) {
                    logger.error("Error sending response", e);
                } finally {
                    asyncContext.complete();
                }
                return;
            }

            String username = usernameOpt.get();
            logger.debug("Processing Discord link request: code={}, username={}, discordId={}, ip={}", code, username, discordId, clientIp);

            // Get the account from database
            CompletableFuture<Optional<Account>> accountFuture = plugin.getDatabaseManager().getAccountByUsername(username);

            accountFuture.thenAccept(accountOpt -> {
                if (accountOpt.isEmpty()) {
                    logger.debug("Account not found for username: {}", username);
                    try {
                        sendJsonResponse(resp, 404, createErrorResponse("Account not found"));
                    } catch (IOException e) {
                        logger.error("Error sending response", e);
                    } finally {
                        asyncContext.complete();
                    }
                    return;
                }

                Account account = accountOpt.get();

                // Check if account already has a Discord ID linked
                if (account.hasLinkedDiscord()) {
                    logger.info("Attempt to link already linked account: {} (current Discord: {}) from IP: {}", username, account.discordId(), clientIp);
                    try {
                        sendJsonResponse(resp, 409, createErrorResponse("This account is already linked to a Discord account"));
                    } catch (IOException e) {
                        logger.error("Error sending response", e);
                    } finally {
                        asyncContext.complete();
                    }
                    return;
                }

                // Check if Discord ID has reached the maximum number of linked accounts
                plugin.getDatabaseManager().countAccountsByDiscordId(discordId)
                        .thenAccept(accountCount -> {
                            if (accountCount >= MAX_ACCOUNTS_PER_DISCORD) {
                                logger.warn("Discord ID {} has reached max linked accounts ({}/{}) (attempted by {} from {})", discordId, accountCount, MAX_ACCOUNTS_PER_DISCORD, username, clientIp);
                                try {
                                    sendJsonResponse(resp, 409, createErrorResponse("This Discord account has reached the maximum number of linked Minecraft accounts"));
                                } catch (IOException e) {
                                    logger.error("Error sending response", e);
                                } finally {
                                    asyncContext.complete();
                                }
                                return;
                            }

                            // Link the Discord account
                            plugin.getDatabaseManager().linkDiscordAccount(account.id(), discordId)
                                    .thenAccept(success -> {
                                        try {
                                            if (success) {
                                                logger.info("Successfully linked Discord account {} to {} from IP: {}", discordId, username, clientIp);

                                                // Create success response
                                                JsonObject response = new JsonObject();
                                                response.addProperty("success", true);
                                                response.addProperty("minecraft_username", username);
                                                response.addProperty("message", "Successfully linked!");

                                                sendJsonResponse(resp, 200, response);
                                            } else {
                                                logger.error("Failed to link Discord account {} to {} from IP: {}", discordId, username, clientIp);
                                                sendJsonResponse(resp, 500, createErrorResponse("Failed to link account. Please try again."));
                                            }
                                        } catch (IOException e) {
                                            logger.error("Error sending response", e);
                                        } finally {
                                            asyncContext.complete();
                                        }
                                    })
                                    .exceptionally(throwable -> {
                                        logger.error("Error linking Discord account", throwable);
                                        try {
                                            sendJsonResponse(resp, 500, createErrorResponse("Internal server error"));
                                        } catch (IOException e) {
                                            logger.error("Error sending response", e);
                                        } finally {
                                            asyncContext.complete();
                                        }
                                        return null;
                                    });
                        })
                        .exceptionally(throwable -> {
                            logger.error("Error checking Discord ID uniqueness", throwable);
                            try {
                                sendJsonResponse(resp, 500, createErrorResponse("Internal server error"));
                            } catch (IOException e) {
                                logger.error("Error sending response", e);
                            } finally {
                                asyncContext.complete();
                            }
                            return null;
                        });
            }).exceptionally(throwable -> {
                logger.error("Error fetching account", throwable);
                try {
                    sendJsonResponse(resp, 500, createErrorResponse("Internal server error"));
                } catch (IOException e) {
                    logger.error("Error sending response", e);
                } finally {
                    asyncContext.complete();
                }
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

        private void sendJsonResponse(HttpServletResponse resp, int statusCode, JsonObject response) throws IOException {
            String jsonResponse = gson.toJson(response);

            resp.setStatus(statusCode);
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.setHeader("Access-Control-Allow-Origin", "*");

            try (PrintWriter writer = resp.getWriter()) {
                writer.write(jsonResponse);
                writer.flush();
            }
        }
    }

    /**
     * Get the real client IP address, considering proxies
     */
    private String getClientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }
        // Take first IP if multiple are present
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }

    /**
     * Rate limiting check per IP address
     */
    private boolean checkRateLimit(String ip) {
        long now = System.currentTimeMillis();

        // Clean up old entries (entries older than 1 minute)
        rateLimitMap.entrySet().removeIf(entry ->
                now - entry.getValue().windowStart > 60000
        );

        RateLimitEntry entry = rateLimitMap.computeIfAbsent(ip, k -> new RateLimitEntry());

        synchronized (entry) {
            // Reset window if it's been more than a minute
            if (now - entry.windowStart > 60000) {
                entry.windowStart = now;
                entry.count.set(0);
            }

            int currentCount = entry.count.incrementAndGet();
            return currentCount <= MAX_REQUESTS_PER_IP;
        }
    }

    /**
     * Rate limiting for Discord IDs (prevent one Discord account from spamming)
     */
    private boolean checkDiscordRateLimit(String discordId) {
        long now = System.currentTimeMillis();

        // Clean up old entries (older than 5 minutes)
        discordLinkAttempts.entrySet().removeIf(entry ->
                now - entry.getValue() > 300000
        );

        Long lastAttempt = discordLinkAttempts.get(discordId);
        if (lastAttempt != null && now - lastAttempt < 60000) {
            // Less than 1 minute since last attempt
            return false;
        }

        discordLinkAttempts.put(discordId, now);
        return true;
    }

    /**
     * Timing-safe secret key comparison to prevent timing attacks
     */
    private boolean isValidSecretKey(String providedSecret) {
        if (providedSecret == null || secretKey == null) {
            return false;
        }

        try {
            // Use MessageDigest for constant-time comparison
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] providedHash = digest.digest(providedSecret.getBytes(StandardCharsets.UTF_8));

            digest.reset();
            byte[] expectedHash = digest.digest(secretKey.getBytes(StandardCharsets.UTF_8));

            // Constant-time comparison
            return MessageDigest.isEqual(providedHash, expectedHash);
        } catch (Exception e) {
            logger.error("Error comparing secret keys", e);
            return false;
        }
    }

    /**
     * Validate input parameters for format and length
     */
    private String validateInputs(String code, String discordId) {
        // Check parameter lengths
        if (code.length() > MAX_PARAM_LENGTH) {
            return "Code parameter too long";
        }
        if (discordId.length() > MAX_PARAM_LENGTH) {
            return "Discord ID parameter too long";
        }

        // Validate code format (6 digits)
        if (!CODE_PATTERN.matcher(code).matches()) {
            return "Invalid code format. Code must be 6 digits";
        }

        // Validate Discord ID format (17-20 digits)
        if (!DISCORD_ID_PATTERN.matcher(discordId).matches()) {
            return "Invalid Discord ID format";
        }

        return null; // All validations passed
    }

    /**
     * Inner class for rate limiting
     */
    private static class RateLimitEntry {
        long windowStart;
        AtomicInteger count;

        RateLimitEntry() {
            this.windowStart = System.currentTimeMillis();
            this.count = new AtomicInteger(0);
        }
    }
}

