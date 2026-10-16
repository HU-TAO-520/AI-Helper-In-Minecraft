package com.qingcheng.songmod;

import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDeviceBase;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;

/**
 * 把解码后的 PCM 写到系统默认音频设备（和游戏声音同时出声），并支持音量。
 * 沿用 ZMusic 的“本地原生播放器”思路，但用 JDK 自带的 javax.sound，跨版本最稳。
 *
 * <p>关键：JLayer 在 {@code Player.play()} 里<b>先</b>调 {@code open()}、<b>之后</b>才读第一帧，
 * 所以 {@code open()} 时 {@code Decoder} 还没有输出格式（频率/声道都是 0）。因此这里
 * <b>延迟到第一次 write 时</b>才真正建 {@link SourceDataLine}（与 JLayer 自带设备一致）。
 */
public class VolumeAudioDevice extends AudioDeviceBase {

    private SourceDataLine line;
    private float volume;

    public VolumeAudioDevice(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
    }

    @Override
    protected void openImpl() throws JavaLayerException {
        // 故意留空：格式要等第一帧解码后才知道。
    }

    private void ensureLine() throws JavaLayerException {
        if (line != null) {
            return;
        }
        Decoder decoder = getDecoder();
        int frequency = decoder.getOutputFrequency();
        int channels = decoder.getOutputChannels();
        if (frequency <= 0 || channels <= 0) {
            throw new JavaLayerException(
                    "无效的音频参数（" + frequency + " Hz / " + channels + " 声道）");
        }
        AudioFormat format = new AudioFormat(frequency, 16, channels, true, false);
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine created;
            if (AudioSystem.isLineSupported(info)) {
                created = (SourceDataLine) AudioSystem.getLine(info);
            } else {
                // 有些设备只接受默认格式，退回一条通用线路。
                created = (SourceDataLine) AudioSystem.getSourceDataLine(format);
            }
            created.open(format, 1 << 16);
            line = created;
            applyVolume();
            line.start();
        } catch (Exception e) {
            throw new JavaLayerException("无法打开音频设备: " + e.getMessage(), e);
        }
    }

    @Override
    protected void writeImpl(short[] samples, int offs, int len) throws JavaLayerException {
        ensureLine();
        byte[] bytes = toByteArray(samples, offs, len);
        line.write(bytes, 0, bytes.length);
    }

    @Override
    protected void flushImpl() {
        if (line != null) {
            line.drain();
        }
    }

    @Override
    protected void closeImpl() {
        if (line != null) {
            try {
                line.stop();
                line.close();
            } finally {
                line = null;
            }
        }
    }

    @Override
    public int getPosition() {
        // 我们不在协议里上报进度，返回 0 即可。
        return 0;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
        applyVolume();
    }

    private void applyVolume() {
        if (line == null) {
            return;
        }
        try {
            FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            float v = Math.max(0.0001f, this.volume);
            float db = (float) (20.0 * Math.log10(v));
            db = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db));
            gain.setValue(db);
        } catch (Exception ignored) {
            // 设备没有主音量控制
        }
    }

    private static byte[] toByteArray(short[] samples, int offs, int len) {
        byte[] out = new byte[len * 2];
        int idx = 0;
        for (int i = 0; i < len; i++) {
            short s = samples[offs + i];
            out[idx++] = (byte) (s & 0xff);
            out[idx++] = (byte) ((s >>> 8) & 0xff);
        }
        return out;
    }
}
