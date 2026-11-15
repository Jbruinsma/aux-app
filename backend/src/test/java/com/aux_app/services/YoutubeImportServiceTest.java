package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import com.aux_app.dto.imports.YoutubeImport;
import com.aux_app.dto.imports.YoutubeImportStatus;
import com.aux_app.dto.users.UserRole;
import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;

// YouTube import: what input reaches yt-dlp, and the job lifecycle. yt-dlp is a shell script that logs its arguments
// and environment, then plays back canned output picked by the `mode` file
class YoutubeImportServiceTest {

    private static final String ID = "dQw4w9WgXcQ";

    @TempDir
    Path tmp;
    private Path fixtures;
    private YoutubeImportService service;
    private final UserEntity tester = user("tester", UserRole.TESTER);

    @BeforeEach
    void setUp() throws IOException {
        fixtures = Files.createDirectories(tmp.resolve("fixtures"));
        Files.write(fixtures.resolve("audio.mp3"), mp3Frames(31));
        info(ID, 31.0, "not_live");
        mode("ok");
        Path script = fixtures.resolve("yt-dlp");
        Files.writeString(script, """
                #!/bin/sh
                F='%s'
                printf '%%s\\n' "$@" '---' >> "$F/args"
                env > "$F/env"
                dir=""; dump=0
                while [ $# -gt 0 ]; do
                  case "$1" in
                    --paths) dir="$2"; shift ;;
                    --dump-single-json) dump=1 ;;
                  esac
                  shift
                done
                case "$(cat "$F/mode")" in
                  blocked) echo "ERROR: [youtube] x: Sign in to confirm you're not a bot" >&2; exit 1 ;;
                  private) echo "ERROR: [youtube] x: Private video. Sign in if you've been granted access" >&2; exit 1 ;;
                  hang) sleep 30 ;;
                  filtered) [ $dump = 0 ] && exit 0 ;;
                esac
                if [ $dump = 1 ]; then cat "$F/info.json"; else cp "$F/audio.mp3" "$dir/audio.mp3"; fi
                """.formatted(fixtures));
        assertTrue(script.toFile().setExecutable(true));
        service = service(true, Duration.ofSeconds(10));
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    // ---- URL parsing ----

    @Test
    void acceptsYoutubeLinkShapes() {
        for (String url : List.of(
                "https://www.youtube.com/watch?v=" + ID,
                "https://youtube.com/watch?v=" + ID,
                "http://m.youtube.com/watch?v=" + ID,
                "https://music.youtube.com/watch?v=" + ID + "&si=abc",
                "https://www.youtube.com/watch?feature=share&v=" + ID + "&list=PL123&t=42s",
                "https://youtu.be/" + ID,
                "https://youtu.be/" + ID + "?si=xyz&t=10",
                "youtu.be/" + ID,
                "www.youtube.com/watch?v=" + ID,
                "HTTPS://WWW.YOUTUBE.COM/watch?v=" + ID,
                "https://www.youtube.com/shorts/" + ID,
                "https://www.youtube.com/embed/" + ID + "?start=3",
                "https://www.youtube.com/live/" + ID,
                "https://www.youtube.com/watch?v=" + ID + "#t=30",
                "  https://youtu.be/" + ID + "\n"
        )) {
            assertEquals(ID, YoutubeImportService.videoId(url), url);
        }
        assertEquals("-abc_DEF123", YoutubeImportService.videoId("https://youtu.be/-abc_DEF123"));
    }

    @Test
    void rejectsEverythingElse() {
        for (String url : Arrays.asList(
                null, "", "   ", ID,
                // Other hosts, look-alikes, and tricks to make another host look like YouTube
                "https://evil.com/watch?v=" + ID,
                "https://www.youtube.com.evil.com/watch?v=" + ID,
                "https://evilyoutube.com/watch?v=" + ID,
                "https://youtube.com./watch?v=" + ID,
                "https://www.youtube.com@evil.com/watch?v=" + ID,
                "https://user:pass@www.youtube.com/watch?v=" + ID,
                "https://evil.com\\@www.youtube.com/watch?v=" + ID,
                "https://www.yоutube.com/watch?v=" + ID, // Cyrillic о
                "https://www.youtube.com:8080/watch?v=" + ID,
                "https://www.youtube.com:443/watch?v=" + ID,
                // Internal addresses, other schemes
                "http://169.254.169.254/latest/meta-data/",
                "http://localhost:5000/api/users/me",
                "http://127.0.0.1/watch?v=" + ID,
                "file:///etc/passwd",
                "ftp://www.youtube.com/watch?v=" + ID,
                "javascript:alert(1)",
                "ytsearch:never gonna give you up",
                // Not one video
                "https://www.youtube.com/playlist?list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG",
                "https://www.youtube.com/@somechannel",
                "https://www.youtube.com/channel/UC38IQsAvIsxxjztdMZQtwHA",
                "https://www.youtube.com/",
                "https://www.youtube.com/watch",
                "https://www.youtube.com/results?search_query=x",
                // Bad or ambiguous ids
                "https://www.youtube.com/watch?v=dQw4w9WgXc",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQQ",
                "https://www.youtube.com/watch?v=dQw4w9WgXc%51",
                "https://www.youtube.com/watch?v=" + ID + "&v=aaaaaaaaaaa",
                "https://www.youtube.com/watch?v=--exec=rm",
                "https://www.youtube.com/watch?vv=" + ID,
                "https://youtu.be/" + ID + "/extra",
                "https://youtu.be/watch?v=" + ID,
                "https://www.youtube.com/watch/../shorts/" + ID,
                "https://www.youtube.com/shorts/" + ID + "/../x",
                // Anything that could split into more arguments or lines
                "https://www.youtube.com/watch?v=" + ID + " --exec sh",
                "https://www.youtube.com/watch?v=" + ID + "\nhttps://evil.com",
                "https://www.youtube.com/watch?v=" + ID + "\t&x=1",
                "https://youtu.be/" + ID + "?x=" + "a".repeat(200)
        )) {
            AuxException e = assertThrows(AuxException.class, () -> YoutubeImportService.videoId(url), String.valueOf(url));
            assertEquals("INVALID_YOUTUBE_URL", e.getDetails().code(), String.valueOf(url));
        }
    }

    @Test
    void canonicalUrlOnlyTakesIds() {
        assertEquals("https://www.youtube.com/watch?v=" + ID, YoutubeImportService.canonicalUrl(ID));
        assertThrows(IllegalArgumentException.class, () -> YoutubeImportService.canonicalUrl("x&list=y"));
    }

    // ---- Access ----

    @Test
    void onlyTestersAndAdminsWhileEnabled() {
        UserEntity regular = user("regular", UserRole.USER);
        assertFalse(service.isAvailable(regular));
        assertTrue(service.isAvailable(tester));
        assertTrue(service.isAvailable(user("admin", UserRole.ADMIN)));
        assertError(HttpStatus.FORBIDDEN, "YOUTUBE_IMPORT_FORBIDDEN", () -> service.start(regular, "https://youtu.be/" + ID));

        YoutubeImportService off = service(false, Duration.ofSeconds(10));
        try {
            assertFalse(off.isAvailable(tester));
            assertError(HttpStatus.SERVICE_UNAVAILABLE, "YOUTUBE_IMPORT_DISABLED", () -> off.start(tester, "https://youtu.be/" + ID));
        } finally {
            off.shutdown();
        }
    }

    // ---- Jobs ----

    @Test
    void importsAndHandsOverTheMp3Once() throws Exception {
        YoutubeImport started = service.start(tester, "https://www.youtube.com/watch?v=" + ID + "&list=PLevil&si=x");
        assertEquals(ID, started.videoId());

        YoutubeImport done = await(started.importId());
        assertEquals(YoutubeImportStatus.READY, done.status());
        assertEquals("Never Gonna Give", done.title());
        assertEquals(31, done.durationSeconds());
        assertNotNull(done.expiresAt());

        // yt-dlp only ever sees the rebuilt URL, after "--", with config and other extractors off
        List<String> args = Files.readAllLines(fixtures.resolve("args"));
        List<String> first = args.subList(0, args.indexOf("---"));
        assertEquals(List.of("--", "https://www.youtube.com/watch?v=" + ID), first.subList(first.size() - 2, first.size()));
        assertTrue(first.containsAll(List.of("--ignore-config", "--use-extractors", "youtube", "--no-playlist")));
        assertFalse(args.stream().anyMatch(a -> a.contains("evil") || a.contains("list=")), args.toString());
        // Nothing from the app's environment
        for (String line : Files.readAllLines(fixtures.resolve("env"))) {
            String name = line.substring(0, line.indexOf('='));
            assertTrue(Set.of("PATH", "HOME", "LANG", "PWD", "SHLVL", "_", "OLDPWD").contains(name), line);
        }

        YoutubeImportService.Claim claim = service.claim(tester, done.importId());
        assertArrayEquals(mp3Frames(31), claim.mp3());
        assertError(HttpStatus.CONFLICT, "IMPORT_IN_USE", () -> service.claim(tester, done.importId()));
        claim.release();
        service.claim(tester, done.importId()).complete();
        assertError(HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND", () -> service.get(tester, done.importId()));
    }

    @Test
    void someoneElsesImportDoesNotExistForYou() {
        String id = service.start(tester, "https://youtu.be/" + ID).importId();
        UserEntity other = user("other", UserRole.TESTER);
        assertError(HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND", () -> service.get(other, id));
        assertError(HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND", () -> service.claim(other, id));
        assertError(HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND", () -> service.discard(other, id));
    }

    @Test
    void lengthAndLiveAreCheckedBeforeDownloading() throws Exception {
        info(ID, 601.0, "not_live");
        assertFailed(service.start(tester, "https://youtu.be/" + ID), "AUDIO_TOO_LONG");
        // One yt-dlp run (the info), no download
        assertEquals(1, Files.readAllLines(fixtures.resolve("args")).stream().filter("---"::equals).count());

        info(ID, 12.0, "not_live");
        assertFailed(service.start(tester, "https://youtu.be/" + ID), "AUDIO_TOO_SHORT");
        info(ID, 200.0, "is_live");
        assertFailed(service.start(tester, "https://youtu.be/" + ID), "VIDEO_IS_LIVE");
        // yt-dlp answered with a different video than the one asked for
        info("aaaaaaaaaaa", 200.0, "not_live");
        assertFailed(service.start(tester, "https://youtu.be/" + ID), "VIDEO_REJECTED");
    }

    @Test
    void ytDlpErrorsBecomeCodes() throws Exception {
        mode("blocked");
        assertFailed(service.start(tester, "https://youtu.be/" + ID), "IMPORT_BLOCKED");
        mode("private");
        assertFailed(service.start(tester, "https://youtu.be/" + ID), "VIDEO_UNAVAILABLE");
        mode("filtered");
        assertFailed(service.start(tester, "https://youtu.be/" + ID), "VIDEO_REJECTED");
        Files.write(fixtures.resolve("audio.mp3"), "<html>not audio</html>".getBytes());
        mode("ok");
        assertFailed(service.start(tester, "https://youtu.be/" + ID), "UNSUPPORTED_AUDIO_TYPE");
    }

    @Test
    void slowYtDlpIsKilled() throws Exception {
        service.shutdown();
        service = service(true, Duration.ofMillis(300));
        mode("hang");
        assertFailed(service.start(tester, "https://youtu.be/" + ID), "IMPORT_TIMED_OUT");
    }

    @Test
    void discardStopsARunningImport() throws Exception {
        mode("hang");
        String id = service.start(tester, "https://youtu.be/" + ID).importId();
        Path dir = tmp.resolve("work/jobs").resolve(id);
        assertTrue(Files.isDirectory(dir));
        service.discard(tester, id);
        assertError(HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND", () -> service.get(tester, id));
        assertFalse(Files.exists(dir));
    }

    @Test
    void capsImportsInFlightPerUser() throws Exception {
        mode("hang");
        for (int i = 0; i < YoutubeImportService.MAX_IN_FLIGHT_PER_USER; i++) {
            service.start(tester, "https://youtu.be/" + ID);
        }
        assertError(HttpStatus.TOO_MANY_REQUESTS, "IMPORT_LIMIT", () -> service.start(tester, "https://youtu.be/" + ID));
        // Others aren't affected
        service.start(user("other", UserRole.TESTER), "https://youtu.be/" + ID);
    }

    // ---- Helpers ----

    private YoutubeImportService service(boolean enabled, Duration timeout) {
        return new YoutubeImportService(enabled, fixtures.resolve("yt-dlp").toString(), "", "",
                tmp.resolve("work"), 2, timeout, timeout);
    }

    private static UserEntity user(String id, UserRole role) {
        UserEntity user = mock(UserEntity.class);
        when(user.getUserId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        return user;
    }

    private void info(String id, double duration, String liveStatus) throws IOException {
        Files.writeString(fixtures.resolve("info.json"), """
                {"_type": "video", "id": "%s", "title": "Never Gonna\\u0007 Give", "duration": %s,
                 "is_live": %s, "live_status": "%s", "formats": [{"format_id": "251", "url": "https://x"}]}
                """.formatted(id, duration, liveStatus.equals("is_live"), liveStatus));
    }

    private void mode(String mode) throws IOException {
        Files.writeString(fixtures.resolve("mode"), mode);
    }

    private YoutubeImport await(String importId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            YoutubeImport current = service.get(tester, importId);
            if (current.status() == YoutubeImportStatus.READY || current.status() == YoutubeImportStatus.FAILED) {
                return current;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Import " + importId + " didn't finish");
    }

    private void assertFailed(YoutubeImport started, String code) throws InterruptedException {
        YoutubeImport done = await(started.importId());
        assertEquals(YoutubeImportStatus.FAILED, done.status());
        assertEquals(code, done.error().code());
        service.discard(tester, done.importId());
    }

    private static void assertError(HttpStatus status, String code, Runnable call) {
        AuxException e = assertThrows(AuxException.class, call::run);
        assertEquals(status, e.getStatus());
        assertEquals(code, e.getDetails().code());
    }

    // MPEG-1 Layer III, 128kbps, 44.1kHz: 417-byte frames of 1152 samples with silent bodies (as in UploadServiceTest)
    private static byte[] mp3Frames(int seconds) {
        int count = seconds * 44100 / 1152;
        ByteBuffer out = ByteBuffer.allocate(count * 417);
        for (int i = 0; i < count; i++) {
            out.putInt(i * 417, 0xFFFB9000);
        }
        return out.array();
    }
}
