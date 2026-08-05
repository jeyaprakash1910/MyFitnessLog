package com.myfitnesslog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfitnesslog.exception.AppUpdateUnavailableException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the GitHub-backed update resolver.
 *
 * <p>Runs against a stub HTTP server from the JDK rather than a Spring context: the
 * behaviour under test is entirely about how this class talks to the GitHub API,
 * and none of it touches the database. That keeps these tests runnable without the
 * PostgreSQL instance the {@code @SpringBootTest} suite needs.
 *
 * <p>The stub also lets the redirect contract be asserted directly, which is the
 * one part of this integration that cannot be reasoned about from the API docs
 * alone: object storage rejects a request carrying both a pre-signed URL and an
 * {@code Authorization} header, so the second hop must be unauthenticated.
 */
class GitHubAppUpdateServiceTest {

    private static final byte[] APK_BYTES = "not-really-an-apk".getBytes(StandardCharsets.UTF_8);

    private HttpServer server;
    private String baseUrl;

    /** Every request the stub received, in order, for contract assertions. */
    private final List<HttpExchange> received = new ArrayList<>();

    /**
     * Assigned in {@link #startStub()} rather than inline: the asset URLs inside it
     * must be absolute, and the stub's port is not known until it is bound.
     */
    private String latestReleaseJson;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/repos/owner/repo/releases/latest", exchange -> {
            received.add(exchange);
            respond(exchange, 200, latestReleaseJson.getBytes(StandardCharsets.UTF_8));
        });

        // Stands in for the asset API URL: answers 302 to the "storage" path below,
        // exactly as GitHub does.
        server.createContext("/repos/owner/repo/releases/assets/9", exchange -> {
            received.add(exchange);
            exchange.getResponseHeaders().add("Location", baseUrl + "/storage/app-release.apk");
            respond(exchange, 302, new byte[0]);
        });

        server.createContext("/storage/app-release.apk", exchange -> {
            received.add(exchange);
            respond(exchange, 200, APK_BYTES);
        });

        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        latestReleaseJson = releaseJson("v1.2.0", "app-release.apk");
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private AppUpdateService service() {
        return new GitHubAppUpdateService("owner/repo", "test-token", baseUrl, new ObjectMapper());
    }

    @Test
    void latestReleaseIsResolvedFromTheReleaseTagAndApkAsset() {
        AppRelease release = service().getLatestRelease();

        // The leading "v" is a tagging convention, not part of the version the
        // client compares against BuildConfig.VERSION_NAME.
        assertEquals("1.2.0", release.versionName());
        assertEquals("Editable per-exercise notes.", release.releaseNotes());
        assertEquals(24_000_000L, release.sizeBytes());
        assertEquals("app-release.apk", release.apkFileName());
        assertNotNull(release.publishedAt());
    }

    @Test
    void theApkAssetIsSelectedByExtensionNotByPosition() {
        // A real release also carries mapping files and checksums, and GitHub does
        // not promise the APK is first.
        latestReleaseJson = """
                {
                  "tag_name": "v1.2.0",
                  "body": "",
                  "published_at": "2026-08-01T10:00:00Z",
                  "assets": [
                    {"id": 7, "name": "mapping.txt", "size": 100, "url": "%s/repos/owner/repo/releases/assets/7"},
                    {"id": 9, "name": "app-release.apk", "size": 24000000, "url": "%s/repos/owner/repo/releases/assets/9"}
                  ]
                }
                """.formatted(baseUrl, baseUrl);

        assertEquals("app-release.apk", service().getLatestRelease().apkFileName());
    }

    @Test
    void apkDownloadFollowsTheRedirectWithoutForwardingTheAuthorizationHeader() throws IOException {
        byte[] downloaded;
        try (InputStream stream = service().openApk()) {
            downloaded = stream.readAllBytes();
        }
        assertArrayEquals(APK_BYTES, downloaded);

        HttpExchange storageRequest = received.get(received.size() - 1);
        assertEquals("/storage/app-release.apk", storageRequest.getRequestURI().getPath());
        // The load-bearing assertion: object storage rejects a request that bears
        // both a pre-signed URL and an Authorization header, so the token must be
        // dropped on the second hop.
        assertNull(storageRequest.getRequestHeaders().getFirst("Authorization"));

        // ...while the asset API request that produced the redirect must be
        // authenticated and must ask for bytes rather than JSON.
        HttpExchange assetRequest = received.get(received.size() - 2);
        assertEquals("Bearer test-token",
                assetRequest.getRequestHeaders().getFirst("Authorization"));
        assertEquals("application/octet-stream",
                assetRequest.getRequestHeaders().getFirst("Accept"));
    }

    @Test
    void anUnconfiguredBackendReportsUnavailableWithoutCallingGitHub() {
        AppUpdateService unconfigured =
                new GitHubAppUpdateService("owner/repo", "", baseUrl, new ObjectMapper());

        assertThrows(AppUpdateUnavailableException.class, unconfigured::getLatestRelease);
        assertThrows(AppUpdateUnavailableException.class, unconfigured::openApk);
        assertTrue(received.isEmpty());
    }

    @Test
    void aNonSemanticTagIsRefusedRatherThanGuessedAt() {
        latestReleaseJson = releaseJson("nightly-2026-08-01", "app-release.apk");

        AppUpdateUnavailableException thrown =
                assertThrows(AppUpdateUnavailableException.class,
                        () -> service().getLatestRelease());
        assertTrue(thrown.getMessage().contains("nightly-2026-08-01"));
    }

    @Test
    void aReleaseWithNoApkAttachedReportsUnavailable() {
        latestReleaseJson = releaseJson("v1.2.0", "release-notes.txt");

        AppUpdateUnavailableException thrown =
                assertThrows(AppUpdateUnavailableException.class,
                        () -> service().getLatestRelease());
        assertTrue(thrown.getMessage().contains("no .apk asset"));
    }

    @Test
    void aRepositoryTheTokenCannotReadReportsUnavailable() throws IOException {
        // GitHub hides private repositories behind 404 rather than 403, so "no
        // release" and "no access" are the same response and must not surface as a
        // 500.
        server.removeContext("/repos/owner/repo/releases/latest");
        server.createContext("/repos/owner/repo/releases/latest",
                exchange -> respond(exchange, 404, "{\"message\":\"Not Found\"}"
                        .getBytes(StandardCharsets.UTF_8)));

        assertThrows(AppUpdateUnavailableException.class, () -> service().getLatestRelease());
    }

    private String releaseJson(String tag, String assetName) {
        return """
                {
                  "tag_name": "%s",
                  "body": "Editable per-exercise notes.",
                  "published_at": "2026-08-01T10:00:00Z",
                  "assets": [
                    {"id": 9, "name": "%s", "size": 24000000, "url": "BASE/repos/owner/repo/releases/assets/9"}
                  ]
                }
                """.formatted(tag, assetName).replace("BASE", baseUrl);
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }
}
