# MacMotion Web

A **local-only Java + JavaScript** web version of MacMotion Convert. The browser UI sends your MOV to a Java server on `127.0.0.1`; the server calls FFmpeg to create an H.264/AAC MP4 and returns it as a download. Nothing is sent to a cloud service.

## Requirements

- A Java 17 or newer JDK
- Apache Maven
- FFmpeg with the `libx264` encoder enabled

On macOS, one common setup is to install a JDK, [Homebrew](https://brew.sh), and then run:

```sh
brew install maven ffmpeg
```

Confirm the tools are available:

```sh
java -version
mvn -version
ffmpeg -version
```

## Start the app

From this folder, run:

```sh
mvn spring-boot:run
```

Then open [http://localhost:8080](http://localhost:8080) in a browser. Press **Convert to MP4** after choosing a `.mov`; the MP4 will download when conversion finishes.

## Use it through GitHub, without installing VS Code

This project includes a GitHub Codespaces configuration. Codespaces is a browser-based code editor and temporary development computer provided by GitHub.

1. On GitHub, create a new repository named `macmotion-web` and upload every file in this folder, including the hidden `.devcontainer` folder and `.gitignore` file.
2. In that repository, choose **Code** → **Codespaces** → **Create codespace on main**.
3. Wait for setup to finish. The included configuration installs Java 17, Maven, and FFmpeg inside the temporary Codespaces computer.
4. In its terminal, run `mvn spring-boot:run`.
5. GitHub will detect port `8080`; choose **Open in Browser** from the Ports tab to test the website.

Codespaces is excellent for writing and testing the app in a browser, but it is not a permanent website host. GitHub Pages can publish only static HTML, CSS, and JavaScript; it cannot run this Java/FFmpeg converter. A public production version will need a separate Java-capable host with FFmpeg, or a redesigned browser-only converter.

## How it works

1. `index.html`, `styles.css`, and `app.js` provide the JavaScript drop zone and download experience.
2. `ConversionController.java` receives the multipart upload and writes it to a unique temporary folder.
3. It starts FFmpeg without a shell, converting to an MP4 container with H.264 video, AAC audio, and `faststart` metadata.
4. The result streams back to the browser, then the server deletes the temporary source, output, and log files.

The server binds only to `127.0.0.1`, so other devices on your network cannot reach it. Do not expose this development server directly to the internet.

## Project layout

```
src/main/java/com/macmotion/web/   Java/Spring Boot server
src/main/resources/static/         Browser UI (HTML, CSS, JavaScript)
```
