package com.aux_app.services;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.aux_app.dto.imports.YoutubeImport;
import com.aux_app.dto.imports.YoutubeImportStatus;
import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.PreDestroy;
import tools.jackson.databind.json.JsonMapper;

// Turns a YouTube link into an MP3 with yt-dlp (and ffmpeg), in the background. Two phases: start() queues a job
// and returns its id; once it is READY, MusicPieceController's create takes the id in place of an uploaded file and
// the MP3 goes through UploadService like any upload. Testers and admins only, and off unless
// AUX_YOUTUBE_IMPORT_ENABLED=true.
//
// The URL is the dangerous part: yt-dlp fetches whatever it's given, including arbitrary sites through its generic
// extractor (so the VM's metadata endpoint, localhost, ...). User input never reaches yt-dlp. videoId() accepts
// only known YouTube URL shapes and pulls out the 11-character id, and yt-dlp gets a URL rebuilt from that id, after
// "--", with only the YouTube extractors enabled and every config file ignored. It runs without a shell, with an
// empty environment (no secrets), a timeout and its own temp dir.
//
// ponytail: jobs live in memory, so a restart drops them (users just import again). Single instance only.
@Service
public class YoutubeImportService {

    private static final Logger log = LoggerFactory.getLogger(YoutubeImportService.class);

    static final int MAX_URL_LENGTH = 200;
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern YOUTU_BE_PATH = Pattern.compile("/([A-Za-z0-9_-]{11})/?");
    private static final Pattern ID_IN_PATH = Pattern.compile("/(?:shorts|embed|live|v)/([A-Za-z0-9_-]{11})/?");
    private static final Set<String> YOUTUBE_HOSTS =
            Set.of("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com");

    static final Duration INFO_TIMEOUT = Duration.ofSeconds(60);
    static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(4);
    static final Duration READY_TTL = Duration.ofMinutes(30);
    static final Duration FAILED_TTL = Duration.ofMinutes(10);
    // Imports are slow and use the whole CPU while ffmpeg runs, so they're capped well below the request rate limit
    static final int MAX_IN_FLIGHT_PER_USER = 2;
    // READY imports hold up to 25MB of disk each until used, discarded or expired
    static final int MAX_HELD_PER_USER = 5;
    static final int MAX_STARTS_PER_HOUR = 20;
    static final int MAX_QUEUED = 8;
    // yt-dlp's info JSON lists every format; a few MB at most
    static final long MAX_INFO_BYTES = 16L * 1024 * 1024;
    // Same cap as an uploaded MP3 (spring.servlet.multipart.max-file-size)
    static final long MAX_MP3_BYTES = 25L * 1024 * 1024;
    private static final int STDERR_TAIL_BYTES = 4096;
    private static final String AUDIO_FILE = "audio.mp3";

    // A stray parse of yt-dlp's JSON can't run code or reach anything; unknown fields are ignored
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final boolean enabled;
    private final String ytDlpPath;
    private final String ffmpegLocation;
    private final String cookiesFile;
    private final Path jobsDir;
    private final Path cacheDir;
    private final Duration infoTimeout;
    private final Duration downloadTimeout;
    private final ThreadPoolExecutor executor;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    // Start times per user, for MAX_STARTS_PER_HOUR. Guarded by `this`
    private final Map<String, Deque<Instant>> starts = new ConcurrentHashMap<>();

    @Autowired
    public YoutubeImportService(
            @Value("${aux.youtube-import.enabled}") boolean enabled,
            @Value("${aux.youtube-import.yt-dlp-path}") String ytDlpPath,
            @Value("${aux.youtube-import.ffmpeg-location}") String ffmpegLocation,
            @Value("${aux.youtube-import.cookies-file}") String cookiesFile,
            @Value("${aux.youtube-import.work-dir}") String workDir,
            @Value("${aux.youtube-import.concurrency}") int concurrency
    ) {
        this(enabled, ytDlpPath, ffmpegLocation, cookiesFile, Path.of(workDir), concurrency, INFO_TIMEOUT, DOWNLOAD_TIMEOUT);
    }

    // Tests pass a fake yt-dlp and short timeouts
    YoutubeImportService(
            boolean enabled,
            String ytDlpPath,
            String ffmpegLocation,
            String cookiesFile,
            Path workDir,
            int concurrency,
            Duration infoTimeout,
            Duration downloadTimeout
    ) {
        this.enabled = enabled;
        this.ytDlpPath = ytDlpPath;
        this.ffmpegLocation = ffmpegLocation == null || ffmpegLocation.isBlank() ? null : ffmpegLocation;
        this.cookiesFile = cookiesFile == null || cookiesFile.isBlank() ? null : cookiesFile;
        Path root = workDir.toAbsolutePath().normalize();
        this.jobsDir = root.resolve("jobs");
        this.cacheDir = root.resolve("cache");
        this.infoTimeout = infoTimeout;
        this.downloadTimeout = downloadTimeout;
        AtomicInteger threads = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(concurrency, concurrency, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_QUEUED), r -> {
                    Thread t = new Thread(r, "youtube-import-" + threads.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });
        if (enabled) {
            try {
                createPrivateDirectory(root);
                // Jobs don't survive a restart, so whatever is left from the last run is garbage
                deleteDirectory(jobsDir);
                createPrivateDirectory(jobsDir);
                createPrivateDirectory(cacheDir);
            } catch (IOException e) {
                throw new UncheckedIOException("Can't set up the YouTube import dir " + root, e);
            }
            log.info("YouTube import is on (yt-dlp: {}, work dir: {})", ytDlpPath, root);
        }
    }

    // ---- Access ----

    public boolean isAvailable(UserEntity user) {
        return enabled && user.getRole().isTester();
    }

    private void requireAccess(UserEntity user) {
        if (!enabled) {
            throw new AuxException(HttpStatus.SERVICE_UNAVAILABLE, "YOUTUBE_IMPORT_DISABLED",
                    "YouTube import is turned off");
        }
        if (!user.getRole().isTester()) {
            throw new AuxException(HttpStatus.FORBIDDEN, "YOUTUBE_IMPORT_FORBIDDEN",
                    "YouTube import is only open to testers right now");
        }
    }

    // ---- URL handling ----

    // Returns the 11-character video id, or throws INVALID_YOUTUBE_URL. Strict on purpose: only the URL shapes
    // YouTube's own share buttons and address bar produce. Works on the raw (still percent-encoded) parts, so
    // encoding tricks can't sneak anything past the patterns.
    static String videoId(String input) {
        if (input == null) throw invalidUrl();
        String s = input.strip();
        if (s.isEmpty() || s.length() > MAX_URL_LENGTH) throw invalidUrl();
        // Printable ASCII only: no spaces, control characters, or look-alike Unicode hosts
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= 0x20 || c >= 0x7F) throw invalidUrl();
        }
        // "youtu.be/abc" pasted without a scheme
        if (!s.contains("://")) s = "https://" + s;

        URI uri;
        try {
            uri = new URI(s);
        } catch (URISyntaxException e) {
            throw invalidUrl();
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null
                || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
                || uri.getRawUserInfo() != null
                || uri.getPort() != -1) {
            throw invalidUrl();
        }
        host = host.toLowerCase(Locale.ROOT);
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();

        Matcher m;
        if (host.equals("youtu.be")) {
            m = YOUTU_BE_PATH.matcher(path);
            if (m.matches()) return m.group(1);
            throw invalidUrl();
        }
        if (!YOUTUBE_HOSTS.contains(host)) throw invalidUrl();
        m = ID_IN_PATH.matcher(path);
        if (m.matches()) return m.group(1);
        if (path.equals("/watch")) {
            String id = null;
            String query = uri.getRawQuery();
            for (String param : query == null ? new String[0] : query.split("&")) {
                if (param.startsWith("v=")) {
                    // Two v= values: ambiguous, so neither is trusted
                    if (id != null) throw invalidUrl();
                    id = param.substring(2);
                }
            }
            if (id != null && VIDEO_ID.matcher(id).matches()) return id;
        }
        throw invalidUrl();
    }

    static String canonicalUrl(String videoId) {
        // Callers only pass ids from videoId(); checked again because this string goes on a command line
        if (!VIDEO_ID.matcher(videoId).matches()) throw new IllegalArgumentException("Bad video id");
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    private static AuxException invalidUrl() {
        return new AuxException(HttpStatus.BAD_REQUEST, "INVALID_YOUTUBE_URL",
                "Must be a link to a single YouTube video", "url");
    }

    // ---- Jobs ----

    public YoutubeImport start(UserEntity user, String url) {
        requireAccess(user);
        String videoId = videoId(url);
        String owner = user.getUserId();
        Job job;
        synchronized (this) {
            long inFlight = owned(owner).filter(j -> !j.isFinished()).count();
            if (inFlight >= MAX_IN_FLIGHT_PER_USER) {
                throw new AuxException(HttpStatus.TOO_MANY_REQUESTS, "IMPORT_LIMIT",
                        "Wait for your current imports to finish");
            }
            long held = owned(owner).filter(j -> j.status != YoutubeImportStatus.FAILED).count();
            if (held >= MAX_HELD_PER_USER) {
                throw new AuxException(HttpStatus.TOO_MANY_REQUESTS, "IMPORT_LIMIT",
                        "Use or discard your finished imports first");
            }
            Instant now = Instant.now();
            Deque<Instant> recent = starts.computeIfAbsent(owner, k -> new ArrayDeque<>());
            while (!recent.isEmpty() && recent.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) {
                recent.pollFirst();
            }
            if (recent.size() >= MAX_STARTS_PER_HOUR) {
                throw new AuxException(HttpStatus.TOO_MANY_REQUESTS, "IMPORT_LIMIT",
                        "Too many imports this hour, try again later");
            }

            String id = UUID.randomUUID().toString();
            Path dir = jobsDir.resolve(id);
            try {
                createPrivateDirectory(dir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            job = new Job(id, owner, videoId, dir);
            jobs.put(id, job);
            try {
                job.task = executor.submit(() -> run(job));
            } catch (RejectedExecutionException e) {
                jobs.remove(id);
                deleteQuietly(dir);
                throw new AuxException(HttpStatus.SERVICE_UNAVAILABLE, "IMPORT_BUSY",
                        "Too many imports running right now, try again in a minute");
            }
            recent.addLast(now);
        }
        return job.view();
    }

    public YoutubeImport get(UserEntity user, String importId) {
        return find(user, importId).view();
    }

    // Cancels a running import or throws away a finished one
    public void discard(UserEntity user, String importId) {
        Job job = find(user, importId);
        if (job.claimed.get()) {
            throw new AuxException(HttpStatus.CONFLICT, "IMPORT_IN_USE",
                    "This import is being turned into a music piece right now", "importId");
        }
        remove(job);
    }

    // For MusicPieceController's create: takes a READY import so no one else can use it at the same time. Call
    // complete() once the piece is saved (the import is then gone), or release() if creating it failed, so the
    // user can fix the form and send the same import again.
    public Claim claim(UserEntity user, String importId) {
        requireAccess(user);
        Job job = find(user, importId);
        if (job.status != YoutubeImportStatus.READY) {
            throw new AuxException(HttpStatus.CONFLICT, "IMPORT_NOT_READY",
                    job.status == YoutubeImportStatus.FAILED ? "This import failed; start a new one"
                            : "This import isn't ready yet", "importId");
        }
        if (!job.claimed.compareAndSet(false, true)) {
            throw new AuxException(HttpStatus.CONFLICT, "IMPORT_IN_USE",
                    "This import is already being turned into a music piece", "importId");
        }
        try {
            return new Claim(job, Files.readAllBytes(job.dir.resolve(AUDIO_FILE)));
        } catch (IOException | RuntimeException e) {
            job.claimed.set(false);
            log.warn("Couldn't read import {}", job.id, e);
            throw notFound();
        }
    }

    public final class Claim {
        private final Job job;
        private final byte[] mp3;

        private Claim(Job job, byte[] mp3) {
            this.job = job;
            this.mp3 = mp3;
        }

        public byte[] mp3() { return mp3; }

        public void complete() { remove(job); }

        public void release() { job.claimed.set(false); }
    }

    private Job find(UserEntity user, String importId) {
        Job job = importId == null ? null : jobs.get(importId);
        // Someone else's import looks the same as one that doesn't exist
        if (job == null || !job.ownerId.equals(user.getUserId())) throw notFound();
        return job;
    }

    private Stream<Job> owned(String ownerId) {
        return jobs.values().stream().filter(j -> j.ownerId.equals(ownerId));
    }

    private void remove(Job job) {
        jobs.remove(job.id);
        job.cancelled = true;
        Future<?> task = job.task;
        // Interrupts run(), which kills yt-dlp; the process is also killed here in case it's between checks
        if (task != null) task.cancel(true);
        Process process = job.process;
        if (process != null) kill(process);
        deleteQuietly(job.dir);
    }

    private static AuxException notFound() {
        return new AuxException(HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND",
                "Import not found; it may have expired", "importId");
    }

    @Scheduled(fixedRate = 60_000)
    void sweep() {
        Instant now = Instant.now();
        for (Job job : jobs.values()) {
            Instant finished = job.finishedAt;
            if (finished == null || job.claimed.get()) continue;
            Duration ttl = job.status == YoutubeImportStatus.READY ? READY_TTL : FAILED_TTL;
            if (finished.plus(ttl).isBefore(now)) remove(job);
        }
        synchronized (this) {
            starts.values().removeIf(d -> d.isEmpty() || d.peekLast().isBefore(now.minus(Duration.ofHours(1))));
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
        jobs.values().forEach(this::remove);
        // Interrupted workers kill their yt-dlp; give them a moment so nothing outlives the app
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- Running yt-dlp ----

    private void run(Job job) {
        try {
            if (job.cancelled) return;
            job.status = YoutubeImportStatus.FETCHING_INFO;
            VideoInfo info = fetchInfo(job);
            job.title = info.title() == null ? null : cleanTitle(info.title());
            job.durationSeconds = (int) Math.round(info.duration());

            if (job.cancelled) return;
            job.status = YoutubeImportStatus.DOWNLOADING;
            byte[] mp3 = download(job);
            // The same check an upload gets, now, so a bad file fails the import instead of the create
            UploadService.Mp3 parsed = UploadService.parseMp3(mp3);
            job.durationSeconds = parsed.durationSeconds();
            job.status = YoutubeImportStatus.READY;
        } catch (AuxException e) {
            fail(job, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(job, new AuxException(HttpStatus.GONE, "IMPORT_CANCELLED", "Import was cancelled"));
        } catch (Exception e) {
            log.warn("YouTube import {} ({}) failed", job.id, job.videoId, e);
            fail(job, failed());
        } finally {
            job.process = null;
            job.finishedAt = Instant.now();
            // A job discarded while running already left the map; make sure nothing it wrote afterwards stays
            if (job.cancelled) deleteQuietly(job.dir);
        }
    }

    private static void fail(Job job, AuxException e) {
        job.error = e;
        job.status = YoutubeImportStatus.FAILED;
        deleteQuietly(job.dir.resolve(AUDIO_FILE));
    }

    private VideoInfo fetchInfo(Job job) throws IOException, InterruptedException {
        Path out = job.dir.resolve("info.json");
        Path err = job.dir.resolve("info.log");
        int exit = exec(job, command(job, List.of("--skip-download", "--dump-single-json")),
                infoTimeout, ProcessBuilder.Redirect.to(out.toFile()), err);
        if (exit != 0) throw failure(job, err);
        if (Files.size(out) > MAX_INFO_BYTES) throw failed();

        VideoInfo info;
        try {
            info = JSON.readValue(out.toFile(), VideoInfo.class);
        } catch (RuntimeException e) {
            log.warn("Unreadable yt-dlp info for {}", job.videoId, e);
            throw failed();
        }
        // We asked for one video; anything else means yt-dlp resolved the URL to something we didn't ask for
        if (info == null || (info.type() != null && !info.type().equals("video")) || !job.videoId.equals(info.id())) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "VIDEO_REJECTED", "That link isn't a single video", "url");
        }
        String live = info.liveStatus();
        if (Boolean.TRUE.equals(info.isLive()) || (live != null && !live.equals("not_live") && !live.equals("was_live"))) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "VIDEO_IS_LIVE",
                    "Live streams and premieres can't be imported", "url");
        }
        Double duration = info.duration();
        if (duration == null) throw failed();
        if (duration < UploadService.MIN_TRACK_SECONDS) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "AUDIO_TOO_SHORT",
                    "Track must be at least " + UploadService.MIN_TRACK_SECONDS + " seconds", "url");
        }
        if (duration > UploadService.MAX_TRACK_SECONDS) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "AUDIO_TOO_LONG", "Track must be at most 10 minutes", "url");
        }
        return info;
    }

    private byte[] download(Job job) throws IOException, InterruptedException {
        Path err = job.dir.resolve("download.log");
        int exit = exec(job, command(job, List.of(
                "--format", "bestaudio/best",
                "--extract-audio", "--audio-format", "mp3", "--audio-quality", "0",
                // Source size, before conversion; 10 minutes of YouTube audio is ~10MB
                "--max-filesize", "60M",
                // Checked again in case the video changed since fetchInfo
                "--match-filter", "duration >= " + UploadService.MIN_TRACK_SECONDS
                        + " & duration <= " + UploadService.MAX_TRACK_SECONDS + " & !is_live",
                "--no-part", "--no-mtime",
                "--paths", job.dir.toString(),
                "--output", "audio.%(ext)s"
        )), downloadTimeout, ProcessBuilder.Redirect.DISCARD, err);
        if (exit != 0) throw failure(job, err);

        Path audio = job.dir.resolve(AUDIO_FILE);
        // yt-dlp exits 0 without a file when --match-filter or --max-filesize skips the video
        if (!Files.isRegularFile(audio)) {
            String log = tail(err).toLowerCase(Locale.ROOT);
            if (log.contains("max-filesize") || log.contains("larger than max")) throw tooLarge();
            throw new AuxException(HttpStatus.BAD_REQUEST, "VIDEO_REJECTED",
                    "This video can't be imported", "url");
        }
        if (Files.size(audio) > MAX_MP3_BYTES) throw tooLarge();
        return Files.readAllBytes(audio);
    }

    private List<String> command(Job job, List<String> args) {
        List<String> cmd = new ArrayList<>(List.of(
                ytDlpPath,
                // No config files (system, user, portable): every option comes from here
                "--ignore-config",
                // Only the YouTube extractors; never the generic one that downloads from any URL
                "--use-extractors", "youtube",
                "--no-playlist",
                "--no-progress",
                "--color", "never",
                "--cache-dir", cacheDir.toString()
        ));
        if (ffmpegLocation != null) cmd.addAll(List.of("--ffmpeg-location", ffmpegLocation));
        if (cookiesFile != null) cmd.addAll(List.of("--cookies", cookiesFile));
        cmd.addAll(args);
        // "--": nothing after it is read as an option
        cmd.add("--");
        cmd.add(canonicalUrl(job.videoId));
        return cmd;
    }

    private int exec(Job job, List<String> cmd, Duration timeout, ProcessBuilder.Redirect stdout, Path stderr)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(cmd)
                .directory(job.dir.toFile())
                .redirectOutput(stdout)
                .redirectError(stderr.toFile());
        // Nothing from the app's environment (R2 keys, JWT secret if they're ever set as env vars). PATH stays so
        // yt-dlp finds ffmpeg and deno; HOME points at the job dir so it can't read or write ~/.config, plugins etc.
        Map<String, String> env = builder.environment();
        String path = env.getOrDefault("PATH", "/usr/local/bin:/usr/bin:/bin");
        env.clear();
        env.put("PATH", path);
        env.put("HOME", job.dir.toString());
        env.put("LANG", "C.UTF-8");

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            log.error("Can't run yt-dlp ({}); is it installed?", ytDlpPath, e);
            throw new AuxException(HttpStatus.SERVICE_UNAVAILABLE, "IMPORT_FAILED",
                    "YouTube import isn't set up on this server");
        }
        job.process = process;
        process.getOutputStream().close();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                kill(process);
                log.warn("yt-dlp timed out for {}: {}", job.videoId, tail(stderr));
                throw new AuxException(HttpStatus.GATEWAY_TIMEOUT, "IMPORT_TIMED_OUT",
                        "YouTube took too long, try again");
            }
            return process.exitValue();
        } catch (InterruptedException e) {
            kill(process);
            throw e;
        } finally {
            job.process = null;
        }
    }

    // yt-dlp starts ffmpeg (and deno), so the whole tree goes
    private static void kill(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    // Maps yt-dlp's error output to something the user can act on. The raw output is only logged: it can contain
    // server paths and isn't meant for users.
    private static AuxException failure(Job job, Path stderr) {
        String text = tail(stderr);
        log.warn("yt-dlp failed for {} (import {}): {}", job.videoId, job.id, text);
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("not a bot")) {
            return new AuxException(HttpStatus.SERVICE_UNAVAILABLE, "IMPORT_BLOCKED",
                    "YouTube is blocking downloads from this server right now");
        }
        if (lower.contains("confirm your age") || lower.contains("age-restricted") || lower.contains("inappropriate for some users")) {
            return new AuxException(HttpStatus.BAD_REQUEST, "VIDEO_UNAVAILABLE",
                    "Age-restricted videos can't be imported", "url");
        }
        if (lower.contains("private video") || lower.contains("video unavailable") || lower.contains("not available")
                || lower.contains("has been removed") || lower.contains("members-only") || lower.contains("join this channel")) {
            return new AuxException(HttpStatus.BAD_REQUEST, "VIDEO_UNAVAILABLE",
                    "This video is private, removed or not available", "url");
        }
        return failed();
    }

    private static AuxException failed() {
        return new AuxException(HttpStatus.BAD_GATEWAY, "IMPORT_FAILED", "Couldn't get the audio from YouTube");
    }

    private static AuxException tooLarge() {
        return new AuxException(HttpStatus.CONTENT_TOO_LARGE, "AUDIO_TOO_LARGE", "The audio would be over 25MB", "url");
    }

    private static String tail(Path file) {
        try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
            long start = Math.max(0, f.length() - STDERR_TAIL_BYTES);
            byte[] bytes = new byte[(int) (f.length() - start)];
            f.seek(start);
            f.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            return "";
        }
    }

    // Only a suggestion for the piece's name (the create form masks profanity and checks length itself)
    private static String cleanTitle(String title) {
        String clean = title.replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
        return clean.length() > 200 ? clean.substring(0, 200).strip() : clean;
    }

    // ---- Files ----

    private static void createPrivateDirectory(Path dir) throws IOException {
        Files.createDirectories(dir);
        // Covers a dir under a shared /tmp; skipped on file systems without POSIX permissions
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
        }
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            if (Files.isDirectory(path)) deleteDirectory(path);
            else Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Couldn't delete {}", path, e);
        }
    }

    // ---- State ----

    // Fields the worker thread writes and request threads read are volatile; each is written by one thread at a time
    private static final class Job {
        final String id;
        final String ownerId;
        final String videoId;
        final Path dir;
        final AtomicBoolean claimed = new AtomicBoolean();
        volatile YoutubeImportStatus status = YoutubeImportStatus.QUEUED;
        volatile String title;
        volatile Integer durationSeconds;
        volatile AuxException error;
        volatile Instant finishedAt;
        volatile Future<?> task;
        volatile Process process;
        volatile boolean cancelled;

        Job(String id, String ownerId, String videoId, Path dir) {
            this.id = id;
            this.ownerId = ownerId;
            this.videoId = videoId;
            this.dir = dir;
        }

        boolean isFinished() {
            return status == YoutubeImportStatus.READY || status == YoutubeImportStatus.FAILED;
        }

        YoutubeImport view() {
            YoutubeImportStatus s = status;
            Instant finished = finishedAt;
            AuxException e = error;
            return new YoutubeImport(
                    id,
                    s,
                    videoId,
                    title,
                    durationSeconds,
                    "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg",
                    s == YoutubeImportStatus.FAILED && e != null ? e.getDetails() : null,
                    s == YoutubeImportStatus.READY && finished != null ? finished.plus(READY_TTL) : null
            );
        }
    }

    // The few fields of yt-dlp's info JSON that are used
    @JsonIgnoreProperties(ignoreUnknown = true)
    record VideoInfo(
            String id,
            String title,
            Double duration,
            @JsonProperty("_type") String type,
            @JsonProperty("is_live") Boolean isLive,
            @JsonProperty("live_status") String liveStatus
    ) {}
}
