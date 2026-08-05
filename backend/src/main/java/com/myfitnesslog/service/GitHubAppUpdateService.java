package com.myfitnesslog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfitnesslog.exception.AppUpdateUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the latest Android release from the project's <b>GitHub Releases</b>.
 *
 * <p>GitHub is used as the artifact store rather than adding one (ADR-0016):
 * releases are already cut there, so the APK lives next to its tag, notes, and
 * source, and no APK is ever committed to Git or baked into this image. The
 * repository is private, so its release assets need a credential - which is
 * exactly why the client cannot fetch them directly and this class proxies
 * instead. The token is a server-side secret and never reaches a phone.
 *
 * <p>Two details of the GitHub API are load-bearing here:
 * <ul>
 *   <li>An asset download must request {@code Accept: application/octet-stream}
 *       against the asset's <em>API</em> URL. The {@code browser_download_url} is
 *       unauthenticated and 404s for a private repository.
 *   <li>That request answers <b>302</b> to a pre-signed object-storage URL which
 *       carries its own credentials in the query string. The redirect must be
 *       followed <em>without</em> the {@code Authorization} header: object storage
 *       rejects a request bearing two auth mechanisms. So redirects are disabled
 *       and the second hop is issued deliberately, unauthenticated
 *       ({@link #followToStorage}). Letting {@link HttpClient} follow it
 *       automatically forwards the header and fails with an opaque 400.
 * </ul>
 */
@Service
public class GitHubAppUpdateService implements AppUpdateService {

    private static final Logger log = LoggerFactory.getLogger(GitHubAppUpdateService.class);

    /**
     * A release tag naming a semantic version, with an optional {@code v} prefix.
     * Anything else is not a release of the app and is refused rather than guessed
     * at: shipping the wrong version string would make the client's comparison
     * meaningless in a way that is invisible until an update silently never
     * appears.
     */
    private static final Pattern SEMVER_TAG = Pattern.compile("^v?(\\d+\\.\\d+\\.\\d+)$");

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final String repository;
    private final String token;
    private final String apiBaseUrl;
    private final ObjectMapper objectMapper;

    /**
     * Redirects are handled explicitly; see the class comment. {@code NEVER} is a
     * correctness requirement, not a preference.
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(TIMEOUT)
            .build();

    public GitHubAppUpdateService(
            @Value("${app.update.repository:}") String repository,
            @Value("${app.update.github-token:}") String token,
            @Value("${app.update.github-api-base-url:https://api.github.com}") String apiBaseUrl,
            ObjectMapper objectMapper) {
        this.repository = repository == null ? "" : repository.trim();
        this.token = token == null ? "" : token.trim();
        this.apiBaseUrl = stripTrailingSlash(
                apiBaseUrl == null || apiBaseUrl.isBlank() ? "https://api.github.com" : apiBaseUrl);
        this.objectMapper = objectMapper;

        if (!isConfigured()) {
            // Not an error: a backend that never serves updates is a valid
            // deployment, and the endpoints answer 503 rather than 500.
            log.info("app.update.* is not fully configured - the in-app update "
                    + "endpoints will report 503. Set APP_UPDATE_REPOSITORY and "
                    + "APP_UPDATE_GITHUB_TOKEN to enable them (ADR-0016).");
        }
    }

    private boolean isConfigured() {
        return !repository.isEmpty() && !token.isEmpty();
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new AppUpdateUnavailableException(
                    "App updates are not configured on this backend.");
        }
    }

    @Override
    public AppRelease getLatestRelease() {
        requireConfigured();

        JsonNode release = getJson(apiBaseUrl + "/repos/" + repository + "/releases/latest");

        String tag = release.path("tag_name").asText("");
        Matcher matcher = SEMVER_TAG.matcher(tag);
        if (!matcher.matches()) {
            throw new AppUpdateUnavailableException(
                    "The latest release is tagged '" + tag + "', which is not a MAJOR.MINOR.PATCH "
                    + "version. Tag releases as 'v1.2.3'; see docs/RELEASE_CHECKLIST.md.");
        }

        JsonNode apk = findApkAsset(release);
        if (apk == null) {
            throw new AppUpdateUnavailableException(
                    "Release '" + tag + "' has no .apk asset attached.");
        }

        return new AppRelease(
                matcher.group(1),
                release.path("body").asText(""),
                parsePublishedAt(release.path("published_at").asText(null)),
                apk.path("size").asLong(0L),
                apk.path("name").asText("app-release.apk"),
                apk.path("url").asText());
    }

    /**
     * The single {@code .apk} asset on a release.
     *
     * <p>Releases also carry mapping files, checksums, and source archives, so the
     * asset list is filtered by extension rather than by position. The first match
     * wins; a release is expected to attach exactly one APK.
     */
    private static JsonNode findApkAsset(JsonNode release) {
        for (JsonNode asset : release.path("assets")) {
            if (asset.path("name").asText("").toLowerCase().endsWith(".apk")) {
                return asset;
            }
        }
        return null;
    }

    private static Instant parsePublishedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ex) {
            // A missing timestamp is cosmetic; it must not fail the update check.
            log.warn("Could not parse release published_at '{}'", value);
            return null;
        }
    }

    @Override
    public InputStream openApk() {
        requireConfigured();
        String assetUrl = getLatestRelease().apkAssetUrl();

        HttpResponse<InputStream> response = send(
                authenticated(assetUrl)
                        .header("Accept", "application/octet-stream")
                        .build(),
                HttpResponse.BodyHandlers.ofInputStream());

        if (isRedirect(response.statusCode())) {
            return followToStorage(response);
        }
        if (response.statusCode() != 200) {
            closeQuietly(response.body());
            throw new AppUpdateUnavailableException(
                    "Downloading the release APK failed with HTTP " + response.statusCode() + ".");
        }
        // GitHub currently always redirects, but a direct 200 is a valid answer to
        // this request and is handled rather than assumed away.
        return response.body();
    }

    /**
     * Follows a 302 to object storage with <b>no</b> {@code Authorization} header;
     * the pre-signed URL is already credentialed. See the class comment.
     */
    private InputStream followToStorage(HttpResponse<InputStream> redirect) {
        closeQuietly(redirect.body());

        String location = redirect.headers().firstValue("location")
                .orElseThrow(() -> new AppUpdateUnavailableException(
                        "The APK download redirected without a Location header."));

        HttpResponse<InputStream> response = send(
                HttpRequest.newBuilder(URI.create(location)).timeout(TIMEOUT).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            closeQuietly(response.body());
            throw new AppUpdateUnavailableException(
                    "Fetching the release APK from storage failed with HTTP "
                    + response.statusCode() + ".");
        }
        return response.body();
    }

    private JsonNode getJson(String url) {
        HttpResponse<String> response = send(
                authenticated(url).header("Accept", "application/vnd.github+json").build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            // Also what a valid token without access to this repository returns:
            // GitHub hides private repositories rather than admitting they exist.
            throw new AppUpdateUnavailableException(
                    "No published release found for '" + repository + "' (or the configured "
                    + "token cannot read it).");
        }
        if (response.statusCode() != 200) {
            throw new AppUpdateUnavailableException(
                    "GitHub returned HTTP " + response.statusCode() + " for the latest release.");
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException ex) {
            throw new AppUpdateUnavailableException("Could not parse the GitHub response.", ex);
        }
    }

    private HttpRequest.Builder authenticated(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET();
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return httpClient.send(request, handler);
        } catch (IOException ex) {
            throw new AppUpdateUnavailableException("Could not reach GitHub.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AppUpdateUnavailableException("Interrupted while contacting GitHub.", ex);
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Nothing useful to do; the interesting failure is the caller's.
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
