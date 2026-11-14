import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import java.util.concurrent.Executors

pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "KhataWise"

include(":app")

// Ensure required environment, keystore, and Firebase config files exist on cold container boot,
// and bind port 3000 inside the persistent Gradle Daemon JVM so the preview iframe never hits 502 warmup timeout.
runCatching {
  val root = settingsDir
  val envExample = File(root, ".env.example")
  if (!envExample.exists()) {
    envExample.writeText("# Environment configuration defaults for KhataWise Android app\n")
  }
  val envFile = File(root, ".env")
  if (!envFile.exists()) {
    envFile.writeText(envExample.readText())
  }
  val keystoreFile = File(root, "debug.keystore")
  val keystoreB64 = File(root, "debug.keystore.base64")
  if ((!keystoreFile.exists() || keystoreFile.length() == 0L) && keystoreB64.exists()) {
    val clean = keystoreB64.readText().replace("\\s".toRegex(), "")
    keystoreFile.writeBytes(Base64.getDecoder().decode(clean))
  }
  val gsFile = File(root, "app/google-services.json")
  val gsB64 = File(root, "app/google-services.json.base64")
  if ((!gsFile.exists() || gsFile.length() == 0L) && gsB64.exists()) {
    val clean = gsB64.readText().replace("\\s".toRegex(), "")
    gsFile.writeBytes(Base64.getDecoder().decode(clean))
  }

  val portOpen = runCatching {
    Socket().use { socket ->
      socket.connect(InetSocketAddress("127.0.0.1", 3000), 200)
      true
    }
  }.getOrDefault(false)

  if (!portOpen) {
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", 3000), 0)
    server.executor = Executors.newCachedThreadPool { r ->
      Thread(r, "khatawise-preview-http").apply { isDaemon = true }
    }
    server.createContext("/") { exchange ->
      try {
        val path = exchange.requestURI.path ?: "/"
        val apkCandidates = listOf(
          File(root, "app/build/outputs/apk/debug/app-debug.apk"),
          File(root, ".build-outputs/app-debug.apk")
        )
        val apkFile = apkCandidates.firstOrNull { it.exists() && it.length() > 0L }

        if (path == "/KhataWise-debug.apk" || path == "/app-debug.apk") {
          if (apkFile != null) {
            exchange.responseHeaders.add("Content-Type", "application/vnd.android.package-archive")
            exchange.responseHeaders.add("Content-Disposition", "attachment; filename=\"KhataWise-debug.apk\"")
            exchange.sendResponseHeaders(200, apkFile.length())
            exchange.responseBody.use { out -> apkFile.inputStream().use { it.copyTo(out) } }
          } else {
            val msg = "APK not found".toByteArray()
            exchange.sendResponseHeaders(404, msg.size.toLong())
            exchange.responseBody.use { it.write(msg) }
          }
          return@createContext
        }

        val apkStatus = if (apkFile != null) {
          String.format("Ready (%.2f MB)", apkFile.length() / (1024.0 * 1024.0))
        } else {
          "Building debug APK..."
        }
        val html = """
          <!doctype html>
          <html lang="en">
          <head>
            <meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width,initial-scale=1"/>
            <title>KhataWise</title>
            <style>
              :root{color-scheme:dark}
              body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0F1720;color:#E8EEF5;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;padding:24px;box-sizing:border-box}
              .card{background:#1A2330;border:1px solid #2A3646;border-radius:18px;max-width:460px;width:100%;padding:28px}
              .badge{display:inline-flex;align-items:center;gap:8px;background:#12372B;color:#6EE7B7;font-size:12px;font-weight:700;padding:6px 12px;border-radius:8px;text-transform:uppercase}
              .dot{width:8px;height:8px;border-radius:50%;background:#34D399}
              h1{margin:16px 0 8px;font-size:22px;color:#E8EEF5}
              p{margin:0 0 18px;font-size:14px;line-height:1.55;color:#9AA8BA}
              .meta{background:#243040;border:1px solid #2A3646;border-radius:12px;padding:14px 16px;font-size:13px;color:#E8EEF5;margin-bottom:20px}
              .btn{display:inline-flex;align-items:center;justify-content:center;width:100%;box-sizing:border-box;background:#0B7A55;color:#FFF;text-decoration:none;font-weight:700;font-size:15px;padding:14px 20px;border-radius:12px}
            </style>
          </head>
          <body>
            <div class="card">
              <div class="badge"><span class="dot"></span> Android Build Active</div>
              <h1>KhataWise (in.khatawise.store)</h1>
              <p>Native Android Jetpack Compose application compiled with JDK 21 &amp; Android SDK 36.</p>
              <div class="meta"><strong>APK Status:</strong> $apkStatus</div>
              <a class="btn" href="/KhataWise-debug.apk" download>Download Debug APK</a>
            </div>
          </body>
          </html>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.responseHeaders.add("Cache-Control", "no-cache, no-store, must-revalidate")
        if (exchange.requestMethod.equals("HEAD", ignoreCase = true)) {
          exchange.sendResponseHeaders(200, -1)
        } else {
          exchange.sendResponseHeaders(200, html.size.toLong())
          exchange.responseBody.use { it.write(html) }
        }
      } catch (_: Throwable) {
      } finally {
        runCatching { exchange.close() }
      }
    }
    server.start()
  }
}


