package com.myfitnesslog.controller;

import com.myfitnesslog.dto.response.AppUpdateResponse;
import com.myfitnesslog.service.AppRelease;
import com.myfitnesslog.service.AppUpdateService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Distribution endpoints for the Android app itself (ADR-0016).
 *
 * <p>Both sit behind the ordinary {@code X-API-Key} boundary (ADR-0013) - they are
 * not added to the permit-list in {@code SecurityConfig}. The APK is signed with
 * the release key and is not secret in the way workout history is, but it also has
 * no reason to be world-downloadable, and the app already sends the key on every
 * request.
 */
@RestController
@RequestMapping("/api/v1/app")
public class AppUpdateController {

    private final AppUpdateService appUpdateService;

    public AppUpdateController(AppUpdateService appUpdateService) {
        this.appUpdateService = appUpdateService;
    }

    @GetMapping("/latest-version")
    public AppUpdateResponse getLatestVersion() {
        AppRelease release = appUpdateService.getLatestRelease();
        return new AppUpdateResponse(
                release.versionName(),
                release.releaseNotes(),
                release.publishedAt(),
                release.sizeBytes());
    }

    /**
     * Streams the latest release APK.
     *
     * <p>Streamed rather than buffered: the response is tens of megabytes and this
     * runs on a small instance, so holding it in heap risks an OOM that would take
     * the sync API down with it. Content-Length is set from the release metadata so
     * the client can render real download progress instead of a spinner.
     */
    @GetMapping("/apk")
    public ResponseEntity<InputStreamResource> downloadApk() {
        AppRelease release = appUpdateService.getLatestRelease();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
                .contentLength(release.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(release.apkFileName())
                        .build()
                        .toString())
                .body(new InputStreamResource(appUpdateService.openApk()));
    }
}
