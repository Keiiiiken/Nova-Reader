package io.luma.reader.drive

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.security.Principal
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

@RunWith(AndroidJUnit4::class)
class DriveBackupClientTest {
    private class Response(url: URL, private val status: Int, body: String = "", private val headers: Map<String, String> = emptyMap()) : HttpsURLConnection(url) {
        val written = ByteArrayOutputStream()
        private val received = ByteArrayInputStream(body.toByteArray())
        var disconnected = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String) = headers[name]
        override fun getInputStream() = received
        override fun getOutputStream() = written
        override fun disconnect() { disconnected = true }
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getPeerPrincipal(): Principal? = null
        override fun getLocalPrincipal(): Principal? = null
    }

    @Test fun listingUsesOnlyAppDataAndNeverFollowsRedirects() = runBlocking {
        lateinit var response: Response
        val files = DriveBackupClient("memory-token") { url ->
            response = Response(url, 200, """{"files":[{"id":"one","name":"backup.zip","modifiedTime":"2026-10-03T12:00:00Z","size":"123"}]}""")
            response
        }.list()
        assertTrue(response.url.query.contains("spaces=appDataFolder"))
        assertTrue(response.url.query.contains("novaBackup"))
        assertEquals("Bearer memory-token", response.getRequestProperty("Authorization"))
        assertFalse(response.instanceFollowRedirects)
        assertTrue(response.disconnected)
        assertEquals(123L, files.single().size)
        assertEquals("https://www.googleapis.com/auth/drive.appdata", DriveBackupClient.SCOPE)
    }

    @Test fun uploadStoresInPrivateAppFolderAndUsesResumableRanges() = runBlocking {
        val requests = mutableListOf<Response>()
        val file = File.createTempFile("drive-contract", ".zip", ApplicationProvider.getApplicationContext<Context>().cacheDir)
        try {
            file.writeText("backup")
            var progress = 0f
            DriveBackupClient("memory-token") { url ->
                Response(url, if (requests.isEmpty()) 200 else 200, "{}", if (requests.isEmpty()) mapOf("Location" to "https://www.googleapis.com/upload/session/test") else emptyMap())
                    .also(requests::add)
            }.upload(file) { progress = it }
            assertEquals(2, requests.size)
            val metadata = org.json.JSONObject(requests[0].written.toString("UTF-8"))
            assertEquals("appDataFolder", metadata.getJSONArray("parents").getString(0))
            assertEquals("1", metadata.getJSONObject("appProperties").getString("novaBackup"))
            assertEquals("POST", requests[0].requestMethod)
            assertEquals("PUT", requests[1].requestMethod)
            assertEquals("bytes 0-5/6", requests[1].getRequestProperty("Content-Range"))
            assertEquals("backup", requests[1].written.toString("UTF-8"))
            assertEquals(1f, progress)
            assertTrue(requests.all { it.disconnected })
        } finally { file.delete() }
    }

    @Test fun uploadRejectsAnUnexpectedSessionHostBeforeSendingBookBytes() = runBlocking {
        val file = File.createTempFile("drive-contract", ".zip", ApplicationProvider.getApplicationContext<Context>().cacheDir)
        try {
            file.writeText("private book")
            var requests = 0
            val client = DriveBackupClient("token") { url -> requests++; Response(url, 200, "{}", mapOf("Location" to "https://example.org/upload")) }
            val failure = runCatching { client.upload(file) {} }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEquals(1, requests)
        } finally { file.delete() }
    }

    @Test fun failedDownloadRemovesPartialFileAndShowsAuthorizationError() = runBlocking {
        val file = File.createTempFile("drive-contract", ".zip", ApplicationProvider.getApplicationContext<Context>().cacheDir)
        val client = DriveBackupClient("expired") { url -> Response(url, 401) }
        val failure = runCatching { client.download(DriveBackup("id", "backup", "", 10), file) {} }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("caducó"))
        assertFalse(file.exists())
    }

    @Test fun savingUpdatesTheLatestCopyAndDeletesOlderCopiesOnlyAfterSuccess() = runBlocking {
        val requests = mutableListOf<Response>()
        val file = File.createTempFile("drive-replace", ".zip", ApplicationProvider.getApplicationContext<Context>().cacheDir)
        try {
            file.writeText("backup")
            val client = DriveBackupClient("memory-token") { url ->
                val response = when (requests.size) {
                    0 -> Response(url, 200, """{"nextPageToken":"more","files":[{"id":"latest","name":"latest.zip","modifiedTime":"2026-10-10T12:00:00Z","size":"6"}]}""")
                    1 -> Response(url, 200, """{"files":[{"id":"old","name":"old.zip","modifiedTime":"2026-10-09T12:00:00Z","size":"6"}]}""")
                    2 -> Response(url, 200, "{}", mapOf("Location" to "https://www.googleapis.com/upload/session/replace"))
                    3 -> Response(url, 200, "{}")
                    4 -> Response(url, 204)
                    else -> error("Unexpected request")
                }
                response.also(requests::add)
            }
            client.saveLatest(file) {}
            assertTrue(requests[1].url.query.contains("pageToken=more"))
            assertEquals("PATCH", requests[2].requestMethod)
            assertTrue(requests[2].url.path.endsWith("/files/latest"))
            assertFalse(org.json.JSONObject(requests[2].written.toString("UTF-8")).has("parents"))
            assertEquals("PUT", requests[3].requestMethod)
            assertEquals("DELETE", requests[4].requestMethod)
            assertTrue(requests[4].url.path.endsWith("/files/old"))
            assertTrue(requests.all { it.disconnected })
        } finally { file.delete() }
    }

    @Test fun failedReplacementNeverDeletesTheExistingCopies() = runBlocking {
        val requests = mutableListOf<Response>()
        val file = File.createTempFile("drive-replace", ".zip", ApplicationProvider.getApplicationContext<Context>().cacheDir)
        try {
            file.writeText("backup")
            val client = DriveBackupClient("memory-token") { url ->
                val response = when (requests.size) {
                    0 -> Response(url, 200, """{"files":[{"id":"latest","name":"latest.zip","modifiedTime":"2026-10-10T12:00:00Z","size":"6"},{"id":"old","name":"old.zip","modifiedTime":"2026-10-09T12:00:00Z","size":"6"}]}""")
                    1 -> Response(url, 200, "{}", mapOf("Location" to "https://www.googleapis.com/upload/session/replace"))
                    else -> Response(url, 500)
                }
                response.also(requests::add)
            }
            assertNotNull(runCatching { client.saveLatest(file) {} }.exceptionOrNull())
            assertTrue(requests.none { it.requestMethod == "DELETE" })
            assertEquals("PATCH", requests[1].requestMethod)
            assertTrue(requests.all { it.disconnected })
        } finally { file.delete() }
    }

    @Test fun firstSaveCreatesOnePrivateCopy() = runBlocking {
        val requests = mutableListOf<Response>()
        val file = File.createTempFile("drive-first", ".zip", ApplicationProvider.getApplicationContext<Context>().cacheDir)
        try {
            file.writeText("backup")
            val client = DriveBackupClient("memory-token") { url ->
                when (requests.size) {
                    0 -> Response(url, 200, """{"files":[]}""")
                    1 -> Response(url, 200, "{}", mapOf("Location" to "https://www.googleapis.com/upload/session/new"))
                    2 -> Response(url, 200, "{}")
                    else -> error("Unexpected request")
                }.also(requests::add)
            }
            client.saveLatest(file) {}
            assertEquals("POST", requests[1].requestMethod)
            assertEquals("appDataFolder", org.json.JSONObject(requests[1].written.toString("UTF-8")).getJSONArray("parents").getString(0))
            assertEquals(3, requests.size)
        } finally { file.delete() }
    }
}
