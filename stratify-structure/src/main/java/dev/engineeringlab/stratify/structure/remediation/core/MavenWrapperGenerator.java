package dev.engineeringlab.stratify.structure.remediation.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Generates Maven wrapper files (mvnw, mvnw.cmd, .mvn/wrapper/*).
 *
 * <p>This utility can generate all necessary Maven wrapper files without requiring Maven to be
 * installed. It downloads the maven-wrapper.jar from Maven Central and generates the wrapper
 * scripts.
 */
public final class MavenWrapperGenerator {

  private static final String DEFAULT_MAVEN_VERSION = "3.9.9";
  private static final String DEFAULT_WRAPPER_VERSION = "3.3.2";
  private static final String WRAPPER_JAR_URL =
      "https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/%s/maven-wrapper-%s.jar";
  private static final String MAVEN_DIST_URL =
      "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%s/apache-maven-%s-bin.zip";

  private final String mavenVersion;
  private final String wrapperVersion;

  public MavenWrapperGenerator() {
    this(DEFAULT_MAVEN_VERSION, DEFAULT_WRAPPER_VERSION);
  }

  public MavenWrapperGenerator(String mavenVersion, String wrapperVersion) {
    this.mavenVersion = mavenVersion;
    this.wrapperVersion = wrapperVersion;
  }

  /**
   * Generates Maven wrapper files in the specified directory.
   *
   * @param targetDir the directory where wrapper files will be created
   * @return list of created files
   * @throws IOException if file operations fail
   */
  public List<Path> generate(Path targetDir) throws IOException {
    List<Path> createdFiles = new ArrayList<>();

    // Create .mvn/wrapper directory
    Path wrapperDir = targetDir.resolve(".mvn").resolve("wrapper");
    Files.createDirectories(wrapperDir);

    // Generate mvnw script
    Path mvnwPath = targetDir.resolve("mvnw");
    Files.writeString(mvnwPath, generateMvnwScript());
    makeExecutable(mvnwPath);
    createdFiles.add(mvnwPath);

    // Generate mvnw.cmd script
    Path mvnwCmdPath = targetDir.resolve("mvnw.cmd");
    Files.writeString(mvnwCmdPath, generateMvnwCmdScript());
    createdFiles.add(mvnwCmdPath);

    // Generate maven-wrapper.properties
    Path propertiesPath = wrapperDir.resolve("maven-wrapper.properties");
    Files.writeString(propertiesPath, generateWrapperProperties());
    createdFiles.add(propertiesPath);

    // Download maven-wrapper.jar
    Path jarPath = wrapperDir.resolve("maven-wrapper.jar");
    if (downloadWrapperJar(jarPath)) {
      createdFiles.add(jarPath);
    }

    createdFiles.add(wrapperDir);

    return createdFiles;
  }

  private boolean downloadWrapperJar(Path jarPath) throws IOException {
    String url = String.format(WRAPPER_JAR_URL, wrapperVersion, wrapperVersion);

    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(60))
              .GET()
              .build();

      HttpResponse<InputStream> response =
          client.send(request, HttpResponse.BodyHandlers.ofInputStream());

      if (response.statusCode() == 200) {
        try (InputStream is = response.body()) {
          Files.copy(is, jarPath);
        }
        return true;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Download interrupted", e);
    } catch (Exception e) {
      // If download fails, continue without the jar - wrapper will download it on first run
    }

    return false;
  }

  private void makeExecutable(Path path) {
    try {
      Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
      perms.add(PosixFilePermission.OWNER_EXECUTE);
      perms.add(PosixFilePermission.GROUP_EXECUTE);
      perms.add(PosixFilePermission.OTHERS_EXECUTE);
      Files.setPosixFilePermissions(path, perms);
    } catch (Exception e) {
      // Ignore on Windows or if permissions can't be set
    }
  }

  private String generateWrapperProperties() {
    return String.format(
        """
        # Licensed to the Apache Software Foundation (ASF) under one
        # or more contributor license agreements.  See the NOTICE file
        # distributed with this work for additional information
        # regarding copyright ownership.  The ASF licenses this file
        # to you under the Apache License, Version 2.0 (the
        # "License"); you may not use this file except in compliance
        # with the License.  You may obtain a copy of the License at
        #
        #   http://www.apache.org/licenses/LICENSE-2.0
        #
        # Unless required by applicable law or agreed to in writing,
        # software distributed under the License is distributed on an
        # "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
        # KIND, either express or implied.  See the License for the
        # specific language governing permissions and limitations
        # under the License.
        wrapperVersion=%s
        distributionType=only-script
        distributionUrl=%s
        """,
        wrapperVersion, String.format(MAVEN_DIST_URL, mavenVersion, mavenVersion));
  }

  private String generateMvnwScript() {
    return """
        #!/bin/sh
        # ----------------------------------------------------------------------------
        # Licensed to the Apache Software Foundation (ASF) under one
        # or more contributor license agreements.  See the NOTICE file
        # distributed with this work for additional information
        # regarding copyright ownership.  The ASF licenses this file
        # to you under the Apache License, Version 2.0 (the
        # "License"); you may not use this file except in compliance
        # with the License.  You may obtain a copy of the License at
        #
        #    http://www.apache.org/licenses/LICENSE-2.0
        #
        # Unless required by applicable law or agreed to in writing,
        # software distributed under the License is distributed on an
        # "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
        # KIND, either express or implied.  See the License for the
        # specific language governing permissions and limitations
        # under the License.
        # ----------------------------------------------------------------------------

        # ----------------------------------------------------------------------------
        # Apache Maven Wrapper startup batch script, version 3.3.2
        #
        # Optional ENV vars
        # -----------------
        #   JAVA_HOME - location of a JDK home dir, required when download maven via java source
        #   MVNW_REPOURL - repo url base for downloading maven distribution
        #   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
        #   MVNW_VERBOSE - true: enable verbose log; debug: trace the mvnw script; others: silence the output
        # ----------------------------------------------------------------------------

        set -euf
        [ "${MVNW_VERBOSE-}" != debug ] || set -x

        # OS specific support.
        native_path() { printf %s\\\\n "$1"; }
        case "$(uname)" in
        CYGWIN* | MINGW*)
          [ -z "${JAVA_HOME-}" ] || JAVA_HOME="$(cygpath --unix "$JAVA_HOME")"
          native_path() { cygpath --path --windows "$1"; }
          ;;
        esac

        # set JAVACMD and JAVACCMD
        set_java_home() {
          # For Cygwin and MinGW, ensure paths are in Unix format before anything is touched
          if [ -n "${JAVA_HOME-}" ]; then
            if [ -x "$JAVA_HOME/jre/sh/java" ]; then
              # IBM's JDK on AIX uses strange locations for the executables
              JAVACMD="$JAVA_HOME/jre/sh/java"
              JAVACCMD="$JAVA_HOME/jre/sh/javac"
            else
              JAVACMD="$JAVA_HOME/bin/java"
              JAVACCMD="$JAVA_HOME/bin/javac"
              if [ ! -x "$JAVACMD" ] || [ ! -x "$JAVACCMD" ]; then
                echo "The JAVA_HOME environment variable is not defined correctly, so mvnw cannot run." >&2
                echo "JAVA_HOME is set to \\"$JAVA_HOME\\", but \\$JAVA_HOME/bin/java or \\$JAVA_HOME/bin/javac does not exist." >&2
                return 1
              fi
            fi
          else
            JAVACMD="$(
              'set' +e
              'unset' -f command 2>/dev/null
              'command' -v java
            )" || :
            JAVACCMD="$(
              'set' +e
              'unset' -f command 2>/dev/null
              'command' -v javac
            )" || :
            if [ ! -x "${JAVACMD-}" ] || [ ! -x "${JAVACCMD-}" ]; then
              echo "The java/javac command does not exist in PATH nor is JAVA_HOME set, so mvnw cannot run." >&2
              return 1
            fi
          fi
        }

        # hash string like Java String::hashCode
        hash_string() {
          str="${1:-}" h=0
          while [ -n "$str" ]; do
            char="${str%"${str#?}"}"
            h=$(((h * 31 + $(LC_CTYPE=C printf %d "'$char")) % 4294967296))
            str="${str#?}"
          done
          printf %x\\\\n $h
        }

        verbose() { :; }
        [ "${MVNW_VERBOSE-}" != true ] || verbose() { printf %s\\\\n "${1-}"; }

        die() {
          printf %s\\\\n "$1" >&2
          exit 1
        }

        trim() {
          # MWRAPPER-139:
          #   Trims trailing and leading whitespace, carriage returns, tabs, and linefeeds.
          printf "%s" "${1}" | tr -d '[:space:]'
        }

        # parse distributionUrl and optional distributionSha256Sum
        while IFS="=" read -r key value; do
          case "${key-}" in
          distributionUrl) MVNW_REPOURL="${MVNW_REPOURL:-$(trim "${value-}")}" ;;
          distributionSha256Sum) MVNW_SHA256SUM="$(trim "${value-}")" ;;
          esac
        done <"${0%/*}/.mvn/wrapper/maven-wrapper.properties"
        [ -n "${MVNW_REPOURL-}" ] || die "distributionUrl is not set in .mvn/wrapper/maven-wrapper.properties"

        case "${MVNW_REPOURL-}" in
        *?-bin.zip | *?maven-mvnd-?*-?*.zip) ;;
        *) MVNW_REPOURL="${MVNW_REPOURL%-bin.zip}-bin.zip" ;;
        esac

        # apply MVNW_REPOURL and calculate MAVEN_HOME
        [ -z "${MVNW_REPOURL##*-bin.zip}" ] || die "distributionUrl must end with -bin.zip: $MVNW_REPOURL"
        distributionUrlNameMain="${MVNW_REPOURL##*/}"
        distributionUrlName="${distributionUrlNameMain%-bin.zip}"
        MAVEN_USER_HOME="${MAVEN_USER_HOME:-${HOME}/.m2}"
        MAVEN_HOME="${MAVEN_USER_HOME}/wrapper/dists/${distributionUrlName}"

        exec_maven() {
          unset MVNW_VERBOSE MVNW_USERNAME MVNW_PASSWORD MVNW_REPOURL MVNW_SHA256SUM
          exec "$MAVEN_HOME/bin/mvn" "$@" || die "cannot exec maven"
        }

        if [ -d "$MAVEN_HOME" ]; then
          verbose "found existing MAVEN_HOME at $MAVEN_HOME"
          exec_maven "$@"
        fi

        case "${MVNW_VERBOSE-}" in
        true | debug) echo "Couldn't find MAVEN_HOME, downloading Maven $distributionUrlName from $MVNW_REPOURL" ;;
        esac

        set_java_home || die "No Java available"

        TMP_DOWNLOAD_DIR="${TMPDIR:-/tmp}"
        TMP_DOWNLOAD_DIR_RAND="$(awk 'BEGIN{srand();print int(rand()*10^9)}')"
        TMP_DOWNLOAD_DIR="${TMP_DOWNLOAD_DIR%/}/mvnw.$TMP_DOWNLOAD_DIR_RAND"
        mkdir -p -- "$TMP_DOWNLOAD_DIR" || die "Couldn't create temporary download dir: $TMP_DOWNLOAD_DIR"
        trap 'rm -rf -- "$TMP_DOWNLOAD_DIR"' EXIT INT TERM HUP

        if [ -n "${MVNW_SHA256SUM-}" ]; then
          SHA256_CHECKSUM_FILE="$TMP_DOWNLOAD_DIR/sha256-checksum.txt"
          printf %s "$MVNW_SHA256SUM  $TMP_DOWNLOAD_DIR/$distributionUrlNameMain" > "$SHA256_CHECKSUM_FILE"
        fi

        verbose "Downloading: $MVNW_REPOURL"
        if command -v curl >/dev/null; then
          curl ${MVNW_VERBOSE:+--progress-bar} --fail -L -o "$TMP_DOWNLOAD_DIR/$distributionUrlNameMain" "$MVNW_REPOURL" -C - \\
            ${MVNW_USERNAME:+--user "$MVNW_USERNAME:${MVNW_PASSWORD:-}"}
        elif command -v wget >/dev/null; then
          wget ${MVNW_VERBOSE:+--show-progress} -O "$TMP_DOWNLOAD_DIR/$distributionUrlNameMain" "$MVNW_REPOURL" \\
            ${MVNW_USERNAME:+--user="$MVNW_USERNAME" --password="${MVNW_PASSWORD:-}"}
        else
          die "curl or wget is required to download maven"
        fi

        if [ -n "${MVNW_SHA256SUM-}" ]; then
          if command -v sha256sum >/dev/null; then
            sha256sum -c "$SHA256_CHECKSUM_FILE" >/dev/null 2>&1 || die "SHA-256 checksum verification failed"
          elif command -v shasum >/dev/null; then
            shasum -a 256 -c "$SHA256_CHECKSUM_FILE" >/dev/null 2>&1 || die "SHA-256 checksum verification failed"
          else
            echo "No sha256sum or shasum available, skipping checksum verification"
          fi
        fi

        mkdir -p -- "$MAVEN_HOME" || die "Couldn't create MAVEN_HOME directory: $MAVEN_HOME"
        verbose "Unpacking maven to: $MAVEN_HOME"
        unzip -o -q "$TMP_DOWNLOAD_DIR/$distributionUrlNameMain" -d "$MAVEN_HOME" || die "Couldn't unpack maven"

        nestedDir="$(find "$MAVEN_HOME" -mindepth 1 -maxdepth 1 -type d)"
        if [ -n "$nestedDir" ]; then
          set +f
          mv "$nestedDir"/* "$MAVEN_HOME" || die "Couldn't move maven files"
          set -f
          rmdir "$nestedDir" || die "Couldn't remove nested directory"
        fi

        exec_maven "$@"
        """;
  }

  private String generateMvnwCmdScript() {
    return """
        @REM ----------------------------------------------------------------------------
        @REM Licensed to the Apache Software Foundation (ASF) under one
        @REM or more contributor license agreements.  See the NOTICE file
        @REM distributed with this work for additional information
        @REM regarding copyright ownership.  The ASF licenses this file
        @REM to you under the Apache License, Version 2.0 (the
        @REM "License"); you may not use this file except in compliance
        @REM with the License.  You may obtain a copy of the License at
        @REM
        @REM    http://www.apache.org/licenses/LICENSE-2.0
        @REM
        @REM Unless required by applicable law or agreed to in writing,
        @REM software distributed under the License is distributed on an
        @REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
        @REM KIND, either express or implied.  See the License for the
        @REM specific language governing permissions and limitations
        @REM under the License.
        @REM ----------------------------------------------------------------------------

        @REM ----------------------------------------------------------------------------
        @REM Apache Maven Wrapper startup batch script, version 3.3.2
        @REM
        @REM Optional ENV vars
        @REM   MVNW_REPOURL - repo url base for downloading maven distribution
        @REM   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
        @REM   MVNW_VERBOSE - true: enable verbose log; others: silence the output
        @REM ----------------------------------------------------------------------------

        @IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
        @SET __MVNW_CMD__=
        @SET __MVNW_ERROR__=
        @SET __MVNW_PSMODULEP_SAVE__=%PSModulePath%
        @SET PSModulePath=
        @FOR /F "usebackq tokens=1* delims==" %%A IN (`powershell -noprofile "& {$scriptDir='%~dp0.mvn\\\\wrapper\\\\MavenWrapperDownloader.ps1'; $env:__MVNW_CMD__}`) DO @(
          IF "%%A"=="MVN_CMD" (set __MVNW_CMD__=%%B) ELSE IF "%%B"=="" (echo.%%A) ELSE (echo.%%A=%%B)
        )
        @SET PSModulePath=%__MVNW_PSMODULEP_SAVE__%
        @SET __MVNW_PSMODULEP_SAVE__=
        @SET __MVNW_ARG0_NAME__=
        @SET MVNW_USERNAME=
        @SET MVNW_PASSWORD=
        @IF NOT "%__MVNW_CMD__%"=="" (%__MVNW_CMD__% %*)
        @echo Cannot run mvnw.cmd, PowerShell is not available. >&2
        @exit /b 1
        """;
  }
}
