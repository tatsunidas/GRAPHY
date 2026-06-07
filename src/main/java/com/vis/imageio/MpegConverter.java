/**
 * copyright Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.imageio;

import java.io.File;

import javax.swing.JOptionPane;

import com.vis.configuration.Resources;
import com.vis.core.log.Log;
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
            Log.logger.info("MpegConverter: MPEG codec detected (" + decoderName + "). Remuxing without re-compression.");
        }
        // condition 2: uncompressed codec → encode to H.264
        else if (decoderName.contains("rawvideo") || decoderName.contains("raw") || decoderName.contains("bmp") || decoderName.contains("dib")) {
            video.setCodec("libx264");
            Log.logger.info("MpegConverter: Uncompressed codec detected (" + decoderName + "). Encoding to H.264.");
        }
        // condition 3: unsupported codec
        else {
            Log.logger.warning("MpegConverter: Unsupported codec: " + decoderName);
        	JOptionPane.showMessageDialog(null,
                    String.format(Resources.i18n("MpegConverter.error.codec"), decoderName),
                    Resources.i18n("dialog.title.error"), JOptionPane.ERROR_MESSAGE);
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
