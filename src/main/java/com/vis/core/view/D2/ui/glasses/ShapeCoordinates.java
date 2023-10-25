package com.vis.core.view.D2.ui.glasses;


/**
 *
 * @author BabuHussain
 * @version 0.5
 *
 */
public class ShapeCoordinates {

    private int x;
    private int y;
    private int width;
    private int height;

    public ShapeCoordinates(int X1, int Y1, int X2, int Y2) {

        if (X2 - X1 > 0) {
            x = X1;
            width = X2 - X1;
        } else {
            x = X2;
            width = X1 - X2;
        }
        if (Y2 - Y1 > 0) {
            y = Y1;
            height = Y2 - Y1;
        } else {
            y = Y2;
            height = Y1 - Y2;
        }

    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
