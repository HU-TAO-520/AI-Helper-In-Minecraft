package com.qingcheng.zmusicbridge.music;

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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 自己写的歌曲搜索：直接查网易云公开搜索接口，得到歌曲信息 + 一个公开的 https 音频直链
 * （{@code https://music.163.com/song/media/outer/url?id=<id>.mp3}，未登录也能播放非 VIP 歌曲）。
 *
 * <p>因此本插件不依赖 ZMusic 的云 API / device_token / 任何第三方 SDK。
 */
public final class MusicSearch {

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 按关键词搜索第一首歌曲。
     *
     * @return 命中的歌曲，或 {@code null}
     */
    public SongInfo search(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            return null;
        }
        try {
            String url = "https://music.163.com/api/search/get/web?s="
                    + URLEncoder.encode(kw, StandardCharsets.UTF_8)
                    + "&type=1&offset=0&total=true&limit=1";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", UA)
                    .header("Referer", "https://music.163.com/")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return null;
            }
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonObject result = root.has("result") && root.get("result").isJsonObject()
                    ? root.getAsJsonObject("result") : null;
            if (result == null) {
                return null;
            }
            JsonArray songs = result.has("songs") && result.get("songs").isJsonArray()
                    ? result.getAsJsonArray("songs") : null;
            if (songs == null || songs.size() == 0) {
                return null;
            }
            JsonObject song = songs.get(0).getAsJsonObject();
            String id = asString(song.get("id"));
            if (id == null || id.isEmpty()) {
                return null;
            }
            String title = asString(song.get("name"));
            if (title == null || title.isEmpty()) {
                title = kw;
            }
            List<String> artists = new ArrayList<>();
            if (song.has("artists") && song.get("artists").isJsonArray()) {
                for (JsonElement el : song.getAsJsonArray("artists")) {
                    if (el.isJsonObject()) {
                        String name = asString(el.getAsJsonObject().get("name"));
                        if (name != null && !name.isEmpty()) {
                            artists.add(name);
                        }
                    }
                }
            }
            String album = null;
            if (song.has("album") && song.get("album").isJsonObject()) {
                album = asString(song.getAsJsonObject("album").get("name"));
            }
            String audio = "https://music.163.com/song/media/outer/url?id=" + id + ".mp3";
            return new SongInfo("163", id, title, artists, album, audio);
        } catch (Exception e) {
            return null;
        }
    }

    private static String asString(JsonElement el) {
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
            return null;
        }
        return el.getAsString();
    }
}
