package com.qingcheng.zmusicbridge.music;

import java.util.ArrayList;
import java.util.List;

/**
 * 一首歌的最小信息：协议里 {@code song} 字段 + 客户端要下载的 {@code audio.url}。
 */
public final class SongInfo {

    /** 音源标识，须匹配 {@code ^[a-z0-9][a-z0-9._-]{0,63}$}（如 "163"）。 */
    public final String source;
    /** 音源内的歌曲 id。 */
    public final String id;
    public final String title;
    public final List<String> artists;
    public final String album;
    /** 公开可访问的 http(s) 音频直链。 */
    public final String audioUrl;

    public SongInfo(String source, String id, String title, List<String> artists,
                    String album, String audioUrl) {
        this.source = source;
        this.id = id;
        this.title = title;
        this.artists = artists == null ? new ArrayList<>() : artists;
        this.album = album;
        this.audioUrl = audioUrl;
    }

    public String artistText() {
        return artists.isEmpty() ? "未知歌手" : String.join("/", artists);
    }
}
