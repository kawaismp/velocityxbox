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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class LinkApiServer {
    private final VelocityXbox plugin;
    private final Logger logger;
    private final Gson gson;
    private Server server;
    private final String secretKey;

    public LinkApiServer(VelocityXbox plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.gson = new Gson();
        this.secretKey = plugin.getConfigManager().getLinkApiSecret();
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
            // Start async context for handling CompletableFuture operations
            jakarta.servlet.AsyncContext asyncContext = req.startAsync();
            asyncContext.setTimeout(30000); // 30 second timeout

            try {
                // Parse query parameters
                Map<String, String> params = parseQueryParams(req.getQueryString());

                // Validate secret key
                String providedSecret = params.get("secret_key");
                if (providedSecret == null || !providedSecret.equals(secretKey)) {
                    logger.warn("Unauthorized link API request from {}", req.getRemoteAddr());
                    sendJsonResponse(resp, 401, createErrorResponse("Unauthorized: Invalid secret key"));
                    asyncContext.complete();
                    return;
                }

                // Get required parameters
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

                // Process the link request asynchronously
                processLinkRequest(asyncContext, code, discordId);
            } catch (Exception e) {
                logger.error("Error handling link request", e);
                try {
                    sendJsonResponse(resp, 500, createErrorResponse("Internal server error"));
                } catch (Exception ex) {
                    logger.error("Error sending error response", ex);
                }
                asyncContext.complete();
            }
        }

        private void processLinkRequest(jakarta.servlet.AsyncContext asyncContext, String code, String discordId) {
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
            logger.debug("Processing Discord link request: code={}, username={}, discordId={}", code, username, discordId);

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
                    logger.info("Attempt to link already linked account: {} (current Discord: {})", username, account.discordId());
                    try {
                        sendJsonResponse(resp, 409, createErrorResponse("This account is already linked to a Discord account"));
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
                                    logger.info("Successfully linked Discord account {} to {}", discordId, username);

                                    // Create success response
                                    JsonObject response = new JsonObject();
                                    response.addProperty("success", true);
                                    response.addProperty("minecraft_username", username);
                                    response.addProperty("message", "Successfully linked!");

                                    sendJsonResponse(resp, 200, response);
                                } else {
                                    logger.error("Failed to link Discord account {} to {}", discordId, username);
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
}

