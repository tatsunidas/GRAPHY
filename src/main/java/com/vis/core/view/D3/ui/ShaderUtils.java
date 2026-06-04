/**
 * copyright visionary imaging services, inc.
 */
package com.vis.core.view.D3.ui;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * @author tatsunidas
 */
public class ShaderUtils {
    /**
     * クラスパス（resourcesフォルダ等）からシェーダーファイルを読み込む
     * 例: loadShaderAsString("/shaders/volume.frag")
     */
    public static String loadShaderAsString(String resourcePath) {
        try (InputStream in = ShaderUtils.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new RuntimeException("Shader file not found: " + resourcePath);
            }
            try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter("\\A");
                return scanner.hasNext() ? scanner.next() : "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load shader file: " + resourcePath, e);
        }
    }
}
