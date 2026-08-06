package com.myfitnesslog.controller;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The app-update endpoints against a configured artifact store (ADR-0016).
 *
 * <p>{@code GitHubAppUpdateServiceTest} covers how the service talks to GitHub.
 * This covers the half that sits above it and that no service test can reach: that
 * the HTTP responses match the published contract (API_SPECIFICATION.md §7).
 * Those response headers are not cosmetic — {@code Content-Length} is what lets
 * the Android client show real download progress instead of an indeterminate
 * spinner, and it comes from release metadata rather than from the byte stream, so
 * it can silently drift from the body without a test like this.
 *
 * <p>GitHub is replaced by a stub HTTP server started in a static initializer, so
 * the whole path runs offline and deterministically. It must be a static
 * initializer rather than {@code @BeforeAll}: {@link DynamicPropertySource} is
 * evaluated while the application context is built, which happens first, and the
 * port has to be known by then.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AppUpdateControllerTest {

    private static final byte[] APK_BYTES = "pretend-this-is-an-apk".getBytes(StandardCharsets.UTF_8);

    private static final HttpServer STUB;
    private static final String STUB_BASE_URL;

    static {
        try {
            STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            String base = "http://127.0.0.1:" + STUB.getAddress().getPort();

            STUB.createContext("/repos/owner/repo/releases/latest", exchange -> {
                byte[] body = """
                        {
                          "tag_name": "v1.4.0",
                          "body": "Rest timer keeps running in the background.",
                          "published_at": "2026-08-06T09:00:00Z",
                          "assets": [
                            {"id": 11, "name": "checksums.txt", "size": 64,
                             "url": "%s/repos/owner/repo/releases/assets/11"},
                            {"id": 12, "name": "app-release.apk", "size": %d,
                             "url": "%s/repos/owner/repo/releases/assets/12"}
                          ]
                        }
                        """.formatted(base, APK_BYTES.length, base).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });

            // GitHub answers an asset download with a redirect to object storage.
            STUB.createContext("/repos/owner/repo/releases/assets/12", exchange -> {
                exchange.getResponseHeaders().add("Location", base + "/storage/app-release.apk");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            });

            STUB.createContext("/storage/app-release.apk", exchange -> {
                exchange.sendResponseHeaders(200, APK_BYTES.length);
                exchange.getResponseBody().write(APK_BYTES);
                exchange.close();
            });

            STUB.start();
            STUB_BASE_URL = base;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void updateProperties(DynamicPropertyRegistry registry) {
        registry.add("app.update.repository", () -> "owner/repo");
        registry.add("app.update.github-token", () -> "test-token");
        registry.add("app.update.github-api-base-url", () -> STUB_BASE_URL);
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void latestVersionExposesTheReleaseWithoutItsVPrefix() throws Exception {
        mockMvc.perform(get("/api/v1/app/latest-version"))
                .andExpect(status().isOk())
                // Bare MAJOR.MINOR.PATCH: the client parses this to compare against
                // its own BuildConfig.VERSION_NAME, which never carries a 'v'.
                .andExpect(jsonPath("$.versionName").value("1.4.0"))
                .andExpect(jsonPath("$.releaseNotes")
                        .value("Rest timer keeps running in the background."))
                .andExpect(jsonPath("$.publishedAt").exists())
                .andExpect(jsonPath("$.sizeBytes").value(APK_BYTES.length));
    }

    @Test
    void latestVersionDoesNotLeakAnUpdateVerdictOrTheAssetUrl() throws Exception {
        // The contract deliberately withholds both: whether an update applies is the
        // client's decision (one place for the ordering rule), and the asset URL is
        // useless without the server's token, so publishing it would only invite a
        // client to try. See ADR-0016.
        mockMvc.perform(get("/api/v1/app/latest-version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionCode").doesNotExist())
                .andExpect(jsonPath("$.updateAvailable").doesNotExist())
                .andExpect(jsonPath("$.apkAssetUrl").doesNotExist())
                .andExpect(jsonPath("$.apkUrl").doesNotExist());
    }

    @Test
    void apkIsStreamedWithTheHeadersTheClientNeedsForProgress() throws Exception {
        byte[] body = mockMvc.perform(get("/api/v1/app/apk"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.android.package-archive"))
                // From release metadata, not the stream. Without it the download
                // bar has no total and the client falls back to indeterminate.
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, APK_BYTES.length))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("app-release.apk")))
                .andReturn().getResponse().getContentAsByteArray();

        // Proves the redirect hop to object storage was followed and the real bytes
        // came back, rather than an error page or the redirect body.
        assertArrayEquals(APK_BYTES, body);
    }

    @Test
    void theApkAssetIsChosenByExtensionNotByPosition() throws Exception {
        // The stub lists checksums.txt first. A release carries more than the APK,
        // and GitHub does not promise an order.
        mockMvc.perform(get("/api/v1/app/latest-version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sizeBytes").value(APK_BYTES.length));
    }
}
