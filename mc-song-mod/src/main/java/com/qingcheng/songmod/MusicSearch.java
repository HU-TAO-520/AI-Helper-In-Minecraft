package com.qingcheng.songmod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mod 本地的网易云解析（**保底**用：机器人不在线时自己搜歌播放）。
 *
 * <p>玩家可以在「点歌台」界面里填自己的网易云 Cookie（含 {@code MUSIC_U}），
 * 有会员 Cookie 就能放 VIP 完整版。
 */
public final class MusicSearch {

    static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    /** 必须带的基础 Cookie（否则 fee=1 的歌连试听都不给）。 */
    private static final String BASE_COOKIE = "os=pc; appver=2.9.7; channel=netease;";

    private static final Pattern ID_IN_URL = Pattern.compile("[?&]id=(\\d{4,})");
    private static final Pattern PURE_ID = Pattern.compile("^\\d{6,}$");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private MusicSearch() {
    }

    public static Song search(String keyword, String cookie) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            return null;
        }
        String directId = extractId(kw);
        if (directId != null) {
            return byId(directId, cookie);
        }
        try {
            String url = "https://music.163.com/api/search/get/web?s="
                    + URLEncoder.encode(kw, StandardCharsets.UTF_8)
                    + "&type=1&offset=0&total=true&limit=5";
            JsonObject root = getJson(url, cookie);
            if (root == null) {
                return null;
            }
            JsonObject result = (root.has("result") && root.get("result").isJsonObject())
                    ? root.getAsJsonObject("result") : null;
            if (result == null) {
                return null;
            }
            JsonArray songs = (result.has("songs") && result.get("songs").isJsonArray())
                    ? result.getAsJsonArray("songs") : null;
            if (songs == null || songs.isEmpty()) {
                return null;
            }
            Song trialFallback = null;
            int checked = 0;
            for (JsonElement el : songs) {
                if (checked >= 5) {
                    break;
                }
                if (!el.isJsonObject()) {
                    continue;
                }
                checked++;
                Song candidate = toSong(el.getAsJsonObject(), kw);
                if (candidate == null) {
                    continue;
                }
                resolveAudio(candidate, cookie);
                if (candidate.audioUrl != null && !candidate.audioUrl.isEmpty()) {
                    if (!candidate.isTrial()) {
                        return candidate;
                    }
                    if (trialFallback == null) {
                        trialFallback = candidate;
                    }
                } else if (trialFallback == null && candidate.note != null) {
                    trialFallback = candidate;
                }
            }
            return trialFallback;
        } catch (Exception e) {
            return null;
        }
    }

    public static Song byId(String id, String cookie) {
        Song song = null;
        try {
            JsonObject root = getJson("https://music.163.com/api/song/detail/?ids=%5B" + id + "%5D", cookie);
            if (root != null && root.has("songs") && root.get("songs").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("songs");
                if (!arr.isEmpty() && arr.get(0).isJsonObject()) {
                    song = toSong(arr.get(0).getAsJsonObject(), "id " + id);
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        if (song == null) {
            song = new Song(id, "歌曲 " + id, new ArrayList<>(), null);
        }
        resolveAudio(song, cookie);
        return song;
    }

    /** 从文本里抽出网易云歌曲 id（链接里的 id= / 纯数字）。 */
    public static String extractId(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        Matcher m = ID_IN_URL.matcher(t);
        if (m.find()) {
            return m.group(1);
        }
        if (PURE_ID.matcher(t).matches()) {
            return t;
        }
        return null;
    }

    private static void resolveAudio(Song song, String cookie) {
        // 有 Cookie 时优先走 eapi（**认会员**：能拿 VIP 完整版）
        if (cookie != null && !cookie.trim().isEmpty()) {
            JsonObject d0 = eapiPlayerUrl(song.id, cookie);
            if (d0 != null) {
                String url = asString(d0.get("url"));
                if (url != null && !url.isEmpty()) {
                    song.audioUrl = url;
                    JsonElement ft = d0.get("freeTrialInfo");
                    if (ft != null && ft.isJsonObject()) {
                        JsonElement end = ft.getAsJsonObject().get("end");
                        song.trialSeconds = (end != null && end.isJsonPrimitive()) ? end.getAsInt() : -1;
                    }
                    return;
                }
            }
        }
        try {
            JsonObject root = getJson("https://music.163.com/api/song/enhance/player/url?ids=%5B"
                    + song.id + "%5D&br=320000", cookie);
            if (root != null && root.has("data") && root.get("data").isJsonArray()) {
                JsonArray data = root.getAsJsonArray("data");
                if (!data.isEmpty() && data.get(0).isJsonObject()) {
                    JsonObject d0 = data.get(0).getAsJsonObject();
                    String direct = asString(d0.get("url"));
                    if (direct != null && !direct.isEmpty()) {
                        song.audioUrl = direct;
                        JsonElement ft = d0.get("freeTrialInfo");
                        if (ft != null && ft.isJsonObject()) {
                            JsonElement end = ft.getAsJsonObject().get("end");
                            song.trialSeconds = (end != null && end.isJsonPrimitive()) ? end.getAsInt() : -1;
                        }
                        return;
                    }
                    int fee = (d0.has("fee") && d0.get("fee").isJsonPrimitive())
                            ? d0.get("fee").getAsInt() : 0;
                    song.note = fee > 0
                            ? "该歌曲需要网易云会员（可在「点歌台」界面里填自己的 Cookie）"
                            : "该歌曲暂时没有可播放的地址";
                    return;
                }
            }
        } catch (Exception ignored) {
            // 落到下面的兜底
        }
        song.audioUrl = "https://music.163.com/song/media/outer/url?id=" + song.id + ".mp3";
    }

    private static JsonObject getJson(String url, String cookie) {
        try {
            String cookieHeader = BASE_COOKIE
                    + ((cookie == null || cookie.trim().isEmpty()) ? "" : " " + cookie.trim());
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", UA)
                    .header("Referer", "https://music.163.com/")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Cookie", cookieHeader)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return null;
            }
            return JsonParser.parseString(resp.body()).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static final String EAPI_KEY = "e82ckenh8dichen8";

    private static String eapiParams(String path, String data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(("nobody" + path + "use" + data + "md5forencrypt")
                .getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        String payload = path + "-36cd479b6b5-" + data + "-36cd479b6b5-" + hex;
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(EAPI_KEY.getBytes(StandardCharsets.UTF_8), "AES"));
        byte[] enc = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (byte b : enc) {
            out.append(String.format("%02X", b));
        }
        return out.toString();
    }

    /** 走 eapi 取播放信息（认会员）。返回 data[0] 或 null。 */
    private static JsonObject eapiPlayerUrl(String songId, String cookie) {
        try {
            String path = "/api/song/enhance/player/url/v1";
            String data = "{\"ids\":\"[" + songId + "]\",\"level\":\"exhigh\",\"encodeType\":\"mp3\"}";
            String params = eapiParams(path, data);
            String body = "params=" + URLEncoder.encode(params, StandardCharsets.UTF_8);
            String ck = "os=android; appver=9.1.65;"
                    + ((cookie == null || cookie.trim().isEmpty()) ? "" : " " + cookie.trim());
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://interface3.music.163.com" + path))
                    .header("User-Agent",
                            "NeteaseMusic/9.1.65.240927161425(9001065);Dalvik/2.1.0 (Linux; U; Android 14)")
                    .header("Referer", "https://music.163.com/")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Cookie", ck)
                    .timeout(Duration.ofSeconds(12))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return null;
            }
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonArray arr = (root.has("data") && root.get("data").isJsonArray())
                    ? root.getAsJsonArray("data") : null;
            if (arr == null || arr.isEmpty() || !arr.get(0).isJsonObject()) {
                return null;
            }
            return arr.get(0).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static Song toSong(JsonObject song, String fallbackTitle) {
        String id = asString(song.get("id"));
        if (id == null || id.isEmpty()) {
            return null;
        }
        String title = asString(song.get("name"));
        if (title == null || title.isEmpty()) {
            title = fallbackTitle;
        }
        List<String> artists = new ArrayList<>();
        JsonArray ar = firstArray(song, "artists", "ar");
        if (ar != null) {
            for (JsonElement el : ar) {
                if (el.isJsonObject()) {
                    String name = asString(el.getAsJsonObject().get("name"));
                    if (name != null && !name.isEmpty()) {
                        artists.add(name);
                    }
                }
            }
        }
        String album = null;
        JsonObject al = firstObject(song, "album", "al");
        if (al != null) {
            album = asString(al.get("name"));
        }
        return new Song(id, title, artists, album);
    }

    private static JsonArray firstArray(JsonObject o, String... keys) {
        for (String k : keys) {
            JsonElement el = o.get(k);
            if (el != null && el.isJsonArray()) {
                return el.getAsJsonArray();
            }
        }
        return null;
    }

    private static JsonObject firstObject(JsonObject o, String... keys) {
        for (String k : keys) {
            JsonElement el = o.get(k);
            if (el != null && el.isJsonObject()) {
                return el.getAsJsonObject();
            }
        }
        return null;
    }

    private static String asString(JsonElement el) {
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
            return null;
        }
        return el.getAsString();
    }

    /** 命中歌曲。{@code audioUrl} 可能为 null（受限），此时 {@code note} 说明原因。 */
    public static final class Song {
        public final String id;
        public final String title;
        public final List<String> artists;
        public final String album;
        public String audioUrl;
        public String note;
        /** 0 = 完整版；>0 = 试听秒数；-1 = 试听但未知时长。 */
        public int trialSeconds;

        public Song(String id, String title, List<String> artists, String album) {
            this.id = id;
            this.title = title;
            this.artists = artists;
            this.album = album;
        }

        public boolean isTrial() {
            return trialSeconds != 0;
        }

        public String artistText() {
            return artists.isEmpty() ? "未知歌手" : String.join("/", artists);
        }
    }
}
