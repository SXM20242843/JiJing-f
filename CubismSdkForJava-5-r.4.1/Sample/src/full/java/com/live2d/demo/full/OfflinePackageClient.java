package com.live2d.demo.full;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class OfflinePackageClient {

    private static final String TAG = "OfflinePackage";

    private final Context context;
    private final String backendBaseUrl;
    private final String authToken;

    public OfflinePackageClient(Context context, String backendBaseUrl, String authToken) {
        this.context = context.getApplicationContext();
        this.backendBaseUrl = trimTrailingSlash(backendBaseUrl);
        this.authToken = authToken == null ? "" : authToken;
    }

    public OfflinePackageInfo fetchLatest(String areaId) {
        if (areaId == null || areaId.trim().length() == 0 || backendBaseUrl.length() == 0) {
            return null;
        }

        String requestUrl = "";
        try {
            requestUrl = backendBaseUrl + "/api/app/offline-package/latest?areaId="
                    + URLEncoder.encode(areaId.trim(), "UTF-8");
            Log.d(TAG, "[OfflinePackage] latest request url=" + requestUrl);
            String responseBody = httpGetString(requestUrl);
            Log.d(TAG, "[OfflinePackage] latest responseBody=" + responseBody);
            if (responseBody == null || responseBody.length() == 0) {
                return null;
            }

            JSONObject root = new JSONObject(responseBody);
            int code = root.optInt("code", -1);
            if (code != 0 && code != 200) {
                Log.w(TAG, "[OfflinePackage] latest business failed code=" + code
                        + ", msg=" + root.optString("msg", ""));
                return null;
            }

            JSONObject data = root.optJSONObject("data");
            if (data == null) {
                return null;
            }

            return OfflinePackageInfo.fromJson(data);
        } catch (Exception e) {
            Log.w(TAG, "[OfflinePackage] latest failed url=" + requestUrl, e);
            return null;
        }
    }

    public String getLocalPackageVersion(String areaId) {
        OfflinePackageInfo info = getLocalPackageInfo(areaId);
        return info == null ? "" : info.packageVersion;
    }

    public OfflinePackageInfo getLocalPackageInfo(String areaId) {
        if (areaId == null || areaId.trim().length() == 0) {
            return null;
        }

        OfflinePackageInfo info = readLocalPackageInfo(getMetaFile(areaId));
        OfflinePackageInfo manifestInfo = null;
        if (info == null || isEmptyText(info.packageVersion) || isEmptyText(info.contentHash)) {
            manifestInfo = readLocalPackageInfo(getManifestFile(areaId));
        }

        if (info == null) {
            return manifestInfo;
        }
        if (manifestInfo != null) {
            if (isEmptyText(info.packageVersion) && !isEmptyText(manifestInfo.packageVersion)) {
                info.packageVersion = manifestInfo.packageVersion;
            }
            if (isEmptyText(info.contentHash) && !isEmptyText(manifestInfo.contentHash)) {
                info.contentHash = manifestInfo.contentHash;
            }
        }
        return info;
    }

    public boolean hasLocalManifest(String areaId) {
        if (areaId == null || areaId.trim().length() == 0) {
            return false;
        }
        File manifestFile = getManifestFile(areaId);
        return manifestFile.exists() && manifestFile.isFile() && manifestFile.length() > 0L;
    }

    private OfflinePackageInfo readLocalPackageInfo(File file) {
        try {
            if (file == null || !file.exists() || !file.isFile() || file.length() <= 0L) {
                return null;
            }
            InputStream is = new FileInputStream(file);
            String text = readString(is);
            JSONObject json = new JSONObject(text);
            OfflinePackageInfo info = new OfflinePackageInfo();
            info.packageVersion = optLocalString(json, "packageVersion", "package_version", "version");
            info.contentHash = optLocalString(json, "contentHash", "content_hash", "sha256", "hash");
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    private String optLocalString(JSONObject json, String... keys) {
        if (json == null || keys == null) {
            return "";
        }
        for (int i = 0; i < keys.length; i++) {
            String value = json.optString(keys[i], "");
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isEmptyText(String value) {
        return value == null || value.trim().length() == 0;
    }

    public boolean downloadManifest(OfflinePackageInfo info, String fallbackAreaId) {
        if (info == null || info.packageUrl == null || info.packageUrl.trim().length() == 0) {
            return false;
        }

        String areaId = info.areaId != null && info.areaId.trim().length() > 0
                ? info.areaId.trim()
                : fallbackAreaId;
        if (info.areaId == null || info.areaId.trim().length() == 0) {
            info.areaId = areaId;
        }
        String packageUrl = resolvePackageUrl(info.packageUrl);
        boolean urlLooksZip = isZipPackageUrl(packageUrl);

        try {
            File packageDir = getPackageDir(areaId);
            if (!packageDir.exists() && !packageDir.mkdirs()) {
                throw new IllegalStateException("create dir failed: " + packageDir.getAbsolutePath());
            }

            File tempFile = new File(packageDir, urlLooksZip ? "package.tmp.zip" : "manifest.json.tmp");
            if (urlLooksZip) {
                Log.d(TAG, "[OfflinePackage] downloading zip url=" + packageUrl);
            } else {
                Log.d(TAG, "[OfflinePackage] downloading manifest url=" + packageUrl);
            }

            DownloadResult downloadResult = downloadToFile(packageUrl, tempFile);
            boolean packageIsZip = urlLooksZip || isZipContentType(downloadResult.contentType);
            if (packageIsZip && !urlLooksZip) {
                File zipTempFile = new File(packageDir, "package.tmp.zip");
                if (zipTempFile.exists()) {
                    zipTempFile.delete();
                }
                if (!tempFile.renameTo(zipTempFile)) {
                    throw new IllegalStateException("rename zip temp failed: " + tempFile.getAbsolutePath());
                }
                tempFile = zipTempFile;
            }

            if (packageIsZip) {
                Log.d(TAG, "[OfflinePackage] package type=ZIP");
                if (!urlLooksZip) {
                    Log.d(TAG, "[OfflinePackage] downloading zip url=" + packageUrl);
                }
                Log.d(TAG, "[OfflinePackage] zip download success path=" + tempFile.getAbsolutePath());
                return handleZipPackage(info, areaId, packageUrl, tempFile);
            } else {
                Log.d(TAG, "[OfflinePackage] package type=MANIFEST");
                handleManifestPackage(info, areaId, packageUrl, tempFile);
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "[OfflinePackage] download failed: " + e.getMessage(), e);
            return false;
        }
    }

    public File getManifestFile(String areaId) {
        return new File(getPackageDir(areaId), "manifest.json");
    }

    private void handleManifestPackage(
            OfflinePackageInfo info,
            String areaId,
            String resolvedPackageUrl,
            File tempFile
    ) throws Exception {
        File packageDir = getPackageDir(areaId);
        File manifestFile = new File(packageDir, "manifest.json");
        safeReplaceFile(tempFile, manifestFile);
        writeMeta(info, areaId, resolvedPackageUrl, "MANIFEST");
        Log.d(TAG, "[OfflinePackage] download success path=" + manifestFile.getAbsolutePath());
    }

    private boolean handleZipPackage(
            OfflinePackageInfo info,
            String areaId,
            String resolvedPackageUrl,
            File zipFile
    ) throws Exception {
        String expectedHash = normalizeSha256(info.contentHash);
        if (expectedHash.length() > 0) {
            String actualHash = sha256(zipFile);
            Log.d(TAG, "[OfflinePackage] zip hash expected=" + expectedHash + ", actual=" + actualHash);
            if (!expectedHash.equalsIgnoreCase(actualHash)) {
                Log.w(TAG, "[OfflinePackage] hash mismatch");
                zipFile.delete();
                return false;
            }
        }

        File tempDir = getTempPackageDir(areaId);
        Log.d(TAG, "[OfflinePackage] unzip start tempDir=" + tempDir.getAbsolutePath());
        clearDir(tempDir);
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            throw new IllegalStateException("create temp dir failed: " + tempDir.getAbsolutePath());
        }

        unzipSafely(zipFile, tempDir);
        File manifestInZip = findManifestInExtractedDir(tempDir, areaId);
        if (manifestInZip == null) {
            Log.w(TAG, "[OfflinePackage] manifest-like json not found in zip");
            throw new IllegalStateException("manifest-like json not found in zip");
        }

        File packageDir = getPackageDir(areaId);
        if (!packageDir.exists() && !packageDir.mkdirs()) {
            throw new IllegalStateException("create package dir failed: " + packageDir.getAbsolutePath());
        }

        File manifestFile = new File(packageDir, "manifest.json");
        safeReplaceFile(manifestInZip, manifestFile);
        Log.d(TAG, "[OfflinePackage] manifest copied to=" + manifestFile.getAbsolutePath());
        writeMeta(info, areaId, resolvedPackageUrl, "ZIP");
        zipFile.delete();
        clearDir(tempDir);
        Log.d(TAG, "[OfflinePackage] zip package ready area=" + areaId
                + ", version=" + info.packageVersion);
        return true;
    }

    private void writeMeta(
            OfflinePackageInfo info,
            String areaId,
            String resolvedPackageUrl,
            String packageType
    ) throws Exception {
        File metaFile = getMetaFile(areaId);
        JSONObject meta = info.toMetaJson(nowText(), resolvedPackageUrl, packageType);
        File tempMetaFile = new File(metaFile.getParentFile(), "meta.json.tmp");
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(tempMetaFile),
                    StandardCharsets.UTF_8
            ));
            writer.write(meta.toString());
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (Exception ignored) {}
            }
        }
        safeReplaceFile(tempMetaFile, metaFile);
    }

    private File getMetaFile(String areaId) {
        return new File(getPackageDir(areaId), "meta.json");
    }

    private File getPackageDir(String areaId) {
        return new File(context.getFilesDir(), "offline_packages/area_" + areaId);
    }

    private File getTempPackageDir(String areaId) {
        return new File(context.getFilesDir(), "offline_packages/area_" + areaId + "_tmp");
    }

    private DownloadResult downloadToFile(String urlStr, File targetFile) throws Exception {
        HttpURLConnection conn = null;
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        DownloadResult result = new DownloadResult();
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "*/*");
            if (authToken.length() > 0) {
                conn.setRequestProperty("Authorization", authToken);
            }
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IllegalStateException("HTTP " + responseCode);
            }

            result.contentType = conn.getContentType() == null ? "" : conn.getContentType();
            inputStream = conn.getInputStream();
            outputStream = new FileOutputStream(targetFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.flush();
            result.file = targetFile;
            return result;
        } finally {
            if (outputStream != null) {
                try { outputStream.close(); } catch (Exception ignored) {}
            }
            if (inputStream != null) {
                try { inputStream.close(); } catch (Exception ignored) {}
            }
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    private String httpGetString(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        InputStream inputStream = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            if (authToken.length() > 0) {
                conn.setRequestProperty("Authorization", authToken);
            }
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                Log.w(TAG, "[OfflinePackage] HTTP error: " + responseCode);
                return null;
            }

            inputStream = conn.getInputStream();
            return readString(inputStream);
        } finally {
            if (inputStream != null) {
                try { inputStream.close(); } catch (Exception ignored) {}
            }
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    private String readString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } finally {
            try { reader.close(); } catch (Exception ignored) {}
        }
        return sb.toString();
    }

    private String resolvePackageUrl(String packageUrl) {
        String value = packageUrl == null ? "" : packageUrl.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        if (value.startsWith("/")) {
            String resolved = backendBaseUrl + value;
            Log.d(TAG, "[OfflinePackage] packageUrl is relative, resolved=" + resolved);
            return resolved;
        }
        String resolved = backendBaseUrl + "/" + value;
        Log.d(TAG, "[OfflinePackage] packageUrl is relative, resolved=" + resolved);
        return resolved;
    }

    private String nowText() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());
    }

    private String trimTrailingSlash(String value) {
        if (value == null) return "";
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private boolean isZipPackageUrl(String packageUrl) {
        String value = packageUrl == null ? "" : packageUrl.trim().toLowerCase(Locale.US);
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        return value.endsWith(".zip");
    }

    private boolean isZipContentType(String contentType) {
        String value = contentType == null ? "" : contentType.toLowerCase(Locale.US);
        return value.indexOf("zip") >= 0;
    }

    private void unzipSafely(File zipFile, File tempDir) throws Exception {
        String tempCanonical = tempDir.getCanonicalPath();
        if (!tempCanonical.endsWith(File.separator)) {
            tempCanonical = tempCanonical + File.separator;
        }

        ZipInputStream zipInputStream = null;
        try {
            zipInputStream = new ZipInputStream(new FileInputStream(zipFile));
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName() == null ? "" : entry.getName();
                Log.d(TAG, "[OfflinePackage] unzip entry=" + entryName);
                File outFile = new File(tempDir, entryName);
                String outCanonical = outFile.getCanonicalPath();
                if (!outCanonical.startsWith(tempCanonical)) {
                    Log.w(TAG, "[OfflinePackage] unzip skip unsafe entry=" + entryName);
                    zipInputStream.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    if (!outFile.exists() && !outFile.mkdirs()) {
                        throw new IllegalStateException("create unzip dir failed: " + outFile.getAbsolutePath());
                    }
                    zipInputStream.closeEntry();
                    continue;
                }

                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IllegalStateException("create unzip parent failed: " + parent.getAbsolutePath());
                }

                FileOutputStream outputStream = null;
                try {
                    outputStream = new FileOutputStream(outFile);
                    int len;
                    while ((len = zipInputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, len);
                    }
                    outputStream.flush();
                } finally {
                    if (outputStream != null) {
                        try { outputStream.close(); } catch (Exception ignored) {}
                    }
                    zipInputStream.closeEntry();
                }
            }
        } finally {
            if (zipInputStream != null) {
                try { zipInputStream.close(); } catch (Exception ignored) {}
            }
        }
    }

    private File findManifestInExtractedDir(File tempDir, String areaId) {
        File directManifest = firstExistingFile(
                new File(tempDir, "manifest.json"),
                new File(new File(tempDir, "area_" + areaId), "manifest.json"),
                new File(new File(new File(tempDir, "offline_packages"), "area_" + areaId), "manifest.json")
        );
        if (directManifest != null) {
            Log.d(TAG, "[OfflinePackage] manifest candidate found: " + directManifest.getAbsolutePath());
            return directManifest;
        }

        File recursiveManifest = findFirstNamedFileRecursive(tempDir, "manifest.json");
        if (recursiveManifest != null) {
            Log.d(TAG, "[OfflinePackage] manifest candidate found: " + recursiveManifest.getAbsolutePath());
            return recursiveManifest;
        }

        File directOfflinePackage = firstExistingFile(
                new File(tempDir, "offline_package.json"),
                new File(new File(tempDir, "area_" + areaId), "offline_package.json"),
                new File(new File(new File(tempDir, "offline_packages"), "area_" + areaId), "offline_package.json")
        );
        if (directOfflinePackage != null) {
            Log.d(TAG, "[OfflinePackage] offline package json candidate found: "
                    + directOfflinePackage.getAbsolutePath());
            Log.d(TAG, "[OfflinePackage] use package json as manifest: "
                    + directOfflinePackage.getAbsolutePath());
            return directOfflinePackage;
        }

        File recursiveOfflinePackage = findFirstNamedFileRecursive(tempDir, "offline_package.json");
        if (recursiveOfflinePackage != null) {
            Log.d(TAG, "[OfflinePackage] offline package json candidate found: "
                    + recursiveOfflinePackage.getAbsolutePath());
            Log.d(TAG, "[OfflinePackage] use package json as manifest: "
                    + recursiveOfflinePackage.getAbsolutePath());
            return recursiveOfflinePackage;
        }

        File directPackage = firstExistingFile(
                new File(tempDir, "package.json"),
                new File(new File(tempDir, "area_" + areaId), "package.json"),
                new File(new File(new File(tempDir, "offline_packages"), "area_" + areaId), "package.json")
        );
        if (directPackage != null) {
            Log.d(TAG, "[OfflinePackage] use package json as manifest: " + directPackage.getAbsolutePath());
            return directPackage;
        }

        File recursivePackage = findFirstNamedFileRecursive(tempDir, "package.json");
        if (recursivePackage != null) {
            Log.d(TAG, "[OfflinePackage] use package json as manifest: " + recursivePackage.getAbsolutePath());
            return recursivePackage;
        }

        return null;
    }

    private File firstExistingFile(File... files) {
        if (files == null) {
            return null;
        }
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file != null && file.exists() && file.isFile()) {
                return file;
            }
        }
        return null;
    }

    private File findFirstNamedFileRecursive(File dir, String fileName) {
        if (dir == null || !dir.exists()) {
            return null;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isFile() && fileName.equalsIgnoreCase(file.getName())) {
                return file;
            }
        }
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isDirectory()) {
                File found = findFirstNamedFileRecursive(file, fileName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void safeReplaceFile(File source, File target) throws Exception {
        if (source == null || !source.exists()) {
            throw new IllegalStateException("source file missing");
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("create parent failed: " + parent.getAbsolutePath());
        }

        File tempTarget = new File(parent, target.getName() + ".new");
        File backupTarget = new File(parent, target.getName() + ".bak");
        if (tempTarget.exists()) tempTarget.delete();
        if (backupTarget.exists()) backupTarget.delete();

        copyFile(source, tempTarget);

        boolean hadOld = target.exists();
        if (hadOld && !target.renameTo(backupTarget)) {
            tempTarget.delete();
            throw new IllegalStateException("backup old file failed: " + target.getAbsolutePath());
        }

        if (!tempTarget.renameTo(target)) {
            tempTarget.delete();
            if (hadOld && backupTarget.exists()) {
                backupTarget.renameTo(target);
            }
            throw new IllegalStateException("replace file failed: " + target.getAbsolutePath());
        }

        if (hadOld && backupTarget.exists()) {
            backupTarget.delete();
        }
        if (!source.equals(target)) {
            source.delete();
        }
    }

    private void copyFile(File source, File target) throws Exception {
        FileInputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            inputStream = new FileInputStream(source);
            outputStream = new FileOutputStream(target);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.flush();
        } finally {
            if (outputStream != null) {
                try { outputStream.close(); } catch (Exception ignored) {}
            }
            if (inputStream != null) {
                try { inputStream.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void clearDir(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                deleteRecursive(files[i]);
            }
        }
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    deleteRecursive(children[i]);
                }
            }
        }
        file.delete();
    }

    private String normalizeSha256(String hash) {
        String value = hash == null ? "" : hash.trim().toLowerCase(Locale.US);
        if (value.length() != 64) {
            return "";
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return "";
            }
        }
        return value;
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, len);
            }
        } finally {
            if (inputStream != null) {
                try { inputStream.close(); } catch (Exception ignored) {}
            }
        }
        byte[] bytes = digest.digest();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(bytes[i] & 0xff);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }

    private static class DownloadResult {
        File file;
        String contentType = "";
    }
}
