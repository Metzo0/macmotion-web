package com.macmotion.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ConversionController {
    private static final MediaType MP4 = MediaType.parseMediaType("video/mp4");

    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InputStreamResource> convert(
            @RequestParam("movie") MultipartFile movie,
            @RequestParam(value = "quality", defaultValue = "standard") String quality
    ) throws IOException, InterruptedException {
        if (movie.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a MOV file first.");
        }

        String originalName = movie.getOriginalFilename() == null ? "movie.mov" : movie.getOriginalFilename();
        if (!originalName.toLowerCase().endsWith(".mov")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose a .mov file.");
        }

        ensureFfmpegIsInstalled();
        Path jobDirectory = Files.createTempDirectory("macmotion-" + UUID.randomUUID());
        Path source = jobDirectory.resolve("source.mov");
        Path converted = jobDirectory.resolve("converted.mp4");

        try (InputStream input = movie.getInputStream()) {
            Files.copy(input, source, StandardCopyOption.REPLACE_EXISTING);
        }

        List<String> command = buildFfmpegCommand(source, converted, quality);
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(jobDirectory.resolve("ffmpeg.log").toFile())
                .start();

        int exitCode = process.waitFor();
        if (exitCode != 0 || !Files.exists(converted)) {
            deleteDirectory(jobDirectory);
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "FFmpeg could not convert this video. It may use an unsupported codec."
            );
        }

        String downloadName = displayName(originalName) + "-google.mp4";
        InputStreamResource result = new InputStreamResource(new DeletingInputStream(Files.newInputStream(converted), jobDirectory));

        return ResponseEntity.ok()
                .contentType(MP4)
                .contentDisposition(ContentDisposition.attachment().filename(downloadName).build())
                .body(result);
    }

    private List<String> buildFfmpegCommand(Path source, Path destination, String quality) {
        // H.264 + AAC in an MP4 container is widely accepted by Google products.
        if ("compact".equals(quality)) {
            return List.of(
                    "ffmpeg", "-hide_banner", "-y", "-i", source.toString(),
                    "-map", "0:v:0", "-map", "0:a?",
                    "-vf", "scale=-2:720", "-c:v", "libx264", "-preset", "medium", "-crf", "25",
                    "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", destination.toString()
            );
        }
        return List.of(
                "ffmpeg", "-hide_banner", "-y", "-i", source.toString(),
                "-map", "0:v:0", "-map", "0:a?",
                "-c:v", "libx264", "-preset", "medium", "-crf", "22",
                "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", destination.toString()
        );
    }

    private void ensureFfmpegIsInstalled() throws IOException, InterruptedException {
        Process process;
        try {
            process = new ProcessBuilder("ffmpeg", "-version").start();
        } catch (IOException error) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "FFmpeg is not installed. Install it, then restart MacMotion Web."
            );
        }
        if (!process.waitFor(Duration.ofSeconds(3)) || process.exitValue() != 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "FFmpeg is not available.");
        }
    }

    private String displayName(String filename) {
        String safeName = filename.replaceAll("(?i)\\.mov$", "").replaceAll("[^a-zA-Z0-9._-]", "-");
        return safeName.isBlank() ? "movie" : safeName;
    }

    private static void deleteDirectory(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted((first, second) -> second.compareTo(first))
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                    });
        } catch (IOException ignored) { }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleKnownError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(new ApiError(error.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedError(Exception error) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Something unexpected went wrong. Please try a different file."));
    }

    record ApiError(String message) { }

    private static final class DeletingInputStream extends InputStream {
        private final InputStream delegate;
        private final Path directory;

        private DeletingInputStream(InputStream delegate, Path directory) {
            this.delegate = delegate;
            this.directory = directory;
        }

        @Override public int read() throws IOException { return delegate.read(); }
        @Override public int read(byte[] data, int offset, int length) throws IOException {
            return delegate.read(data, offset, length);
        }
        @Override public void close() throws IOException {
            try { delegate.close(); } finally { deleteDirectory(directory); }
        }
    }
}
