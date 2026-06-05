/**
 * copyright visionary imaging services, inc.
 */
package com.vis.core.view.D2.ui.glasses;

import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.util.Arrays;

/**
 * @author tatsunidas
 */
public class SlideGlassTransferable implements Transferable {

    // アプリ内部用のカスタムFlavor（他アプリには読めない秘密のタグ）
    public static final DataFlavor INTERNAL_PANEL_FLAVOR = 
        new DataFlavor(SlideGlass.class, "Internal SlideGlass Panel");

    private SlideGlass sourceSlide;
    private Image anonymizedImage;
    private File tempFile;

    public SlideGlassTransferable(SlideGlass slide, Image img, File file) {
        this.sourceSlide = slide;
        this.anonymizedImage = img;
        this.tempFile = file;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{
            INTERNAL_PANEL_FLAVOR,        // アプリ内（並び替え・フュージョン用）
            DataFlavor.imageFlavor,       // PowerPoint等（画像ペースト用）
            DataFlavor.javaFileListFlavor // デスクトップ等（ファイルコピペ用）
        };
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        for (DataFlavor supported : getTransferDataFlavors()) {
            if (supported.equals(flavor)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (flavor.equals(INTERNAL_PANEL_FLAVOR)) {
            return sourceSlide; 
        } else if (flavor.equals(DataFlavor.imageFlavor)) {
            return anonymizedImage;
        } else if (flavor.equals(DataFlavor.javaFileListFlavor)) {
            return Arrays.asList(tempFile);
        }
        throw new UnsupportedFlavorException(flavor);
    }
}