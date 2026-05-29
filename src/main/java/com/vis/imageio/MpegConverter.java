/**
 * copyright Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.imageio;

import java.io.File;

import javax.swing.JOptionPane;

import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.encode.VideoAttributes;
import ws.schild.jave.info.MultimediaInfo;
import ws.schild.jave.info.VideoInfo;

public class MpegConverter {

    /**
     * コーデックを判定し、MPEGならAS-ISコピー、非圧縮ならH.264へ変換、それ以外はエラーを出すことで、圧縮の連鎖を防止。
     */
    public static MultimediaInfo convertToH264Mp4WithCheck(File sourceVideo, File targetMp4) throws Exception {
        
        // 1. 動画ファイルの情報を解析
        MultimediaObject multimediaObject = new MultimediaObject(sourceVideo);
        MultimediaInfo info = multimediaObject.getInfo();
        VideoInfo videoInfo = info.getVideo();
        
        if (videoInfo == null) {
            throw new IllegalArgumentException("映像ストリームが見つかりません。");
        }

        // FFmpegが認識したデコーダー（コーデック）名を取得し、小文字に変換
        String decoderName = videoInfo.getDecoder().toLowerCase();
        
        VideoAttributes video = new VideoAttributes();

        // --- 条件分岐ロジック ---

        // 条件1: もしコーデックがすでにMPEG系ならばcopyで進める
        // ※ DICOMのVideoSOPで最も標準的なH.264(MPEG-4 AVC)やH.265(HEVC)、レガシーMPEGを判定
        if (decoderName.contains("h264") || decoderName.contains("hevc") || decoderName.contains("mpeg")) {
            video.setCodec("copy");
            System.out.println("Info: MPEGコーデック(" + decoderName + ")を検出しました。再圧縮なし(copy)でRemuxします。");
        } 
        // 条件2: もし非圧縮なコーデックなら、MPEG(H.264)にする
        // ※ FFmpegでは非圧縮は通常「rawvideo」や「bmp」「dib」等として認識されます
        else if (decoderName.contains("rawvideo") || decoderName.contains("raw") || decoderName.contains("bmp") || decoderName.contains("dib")) {
            video.setCodec("libx264"); // H.264へエンコード
            System.out.println("Info: 非圧縮コーデック(" + decoderName + ")を検出しました。H.264(MPEG)へエンコードします。");
        } 
        // 条件3: コーデックがMPEGでも非圧縮形式でもない場合、エラーをだす
        // ※ 例: mjpeg, vp8, vp9, wmv など
        else {
        	JOptionPane.showMessageDialog(null, "Error: This codec is not supported. (" + decoderName + "), you can input MPEG-family or non-compression type to convert it DICOM.");
            return null;
        	// throw new IllegalArgumentException("Error: This codec is not supported. (" + decoderName + "), you can input MPEG-family or non-compression type to convert it DICOM.");
        }

        // 音声は不要であればnullでも良いですが、コピーを試みます
        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("copy");

        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("mp4"); // 出力コンテナをMP4に指定
        attrs.setVideoAttributes(video);
        attrs.setAudioAttributes(audio);

        // FFmpegによる処理を実行
        Encoder encoder = new Encoder();
        encoder.encode(multimediaObject, targetMp4, attrs);

        // 変換後の中間ファイルの情報を返す
        return new MultimediaObject(targetMp4).getInfo();
    }
}
