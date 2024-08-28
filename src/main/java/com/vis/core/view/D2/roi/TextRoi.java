package com.vis.core.view.D2.roi;

import ij.*;
import ij.process.*;
import ij.util.*;
import ij.plugin.frame.Recorder;
import ij.plugin.Colors;
import java.awt.geom.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.vis.configuration.ContextKey;
import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.glasses.CanvasGlass;
import com.vis.core.view.D2.ui.glasses.EventGlass;
import com.vis.core.view.D2.ui.glasses.SlideGlass;

/** This class is a rectangular ROI containing text. */
@SuppressWarnings("serial")
public class TextRoi extends RoiObj {

    public static final int LEFT=0, CENTER=1, RIGHT=2;
    static final int MAX_LINES = 50;

    private static final String line1a = "Enter text...";
    private String[] theText = new String[MAX_LINES];
    private static String name = "SansSerif";
    private static int style = Font.PLAIN;
    private static int size = 14;
    private Font font;
    private static boolean antialiasedText = true; // global flag used by text tool
    private static int globalJustification = LEFT;
    private int justification = LEFT;
    private double angle;  // degrees
    private Graphics fontGraphics;
    private static Font defaultFont = new Font(name,style,size);
    private boolean initialize = true;
    
    JTextArea textArea;
    JScrollPane textPane;
    
    int prevCompW = 0;
    int prevCompH = 0;

    /** Creates a TextRoi using the defaultFont.*/
    public TextRoi(int x, int y, String text, SlideGlass slide) {
        this(x, y, text, defaultFont,slide);
    }
    
    /** Use this constructor as a drop-in replacement for ImageProcessor.drawString(). */
    public TextRoi(String text, double x, double y, Font font, SlideGlass slide) {
        super((int)x, (int)y, 1, 1,0,slide);
        init(text,font);
        if (font!=null) {
            Graphics g = getFontGraphics(font);
            FontMetrics metrics = g.getFontMetrics(font);
            Rectangle2D.Double fbounds = getFloatBounds();
            fbounds.y = fbounds.y-metrics.getAscent();
            setBounds(fbounds);
        }
    }

    /** Creates a TextRoi using sub-pixel coordinates.*/
    public TextRoi(double x, double y, String text, SlideGlass slide) {
        super((int)x, (int)y, 1, 1, 0,slide);
        init(text, null);
    }

    /** Creates a TextRoi using the specified location and Font.
     * @see ij.gui.Roi#setStrokeColor
     * @see ij.gui.Roi#setNonScalable
     * @see ij.ImagePlus#setOverlay(ij.gui.Overlay)
     */
	public TextRoi(int x, int y, String text, Font font, SlideGlass slide) {
		super((int) x, (int) y, 1, 1, 0, slide);
		init(text, font);
	}

	/** Creates a TextRoi using the specified sub-pixel location and Font. */
	public TextRoi(double x, double y, String text, Font font, SlideGlass slide) {
		super((int) x, (int) y, 1, 1, 0, slide);
		init(text, font);
	}

	/** Creates a TextRoi using the specified sub-pixel location, size and Font. */
	public TextRoi(double x, double y, double width, double height, String text, Font font, SlideGlass slide) {
		super((int) x, (int) y, (int) width, (int) height, 0, slide);
		init(text, font);
	}
    
    /** Creates a TextRoi using the specified text and location. */
    public static TextRoi create(String text, double x, double y, Font font,SlideGlass slide) {
        return new TextRoi(text, x, y, font, slide);
    }

    /** Obsolete. */
    public static TextRoi create(double x, double y, String text, Font font,SlideGlass slide) {
        return new TextRoi(x, y, text, font,slide);
    }

	private void init(String text, Font font/*null-able*/) {
		setProperty(ContextKey.Description.name(), text);
		String[] lines = Tools.split(text, "\n");
		setType(RoiType.TEXT);
		int count = Math.min(lines.length, MAX_LINES);
		for (int i = 0; i < count; i++) {
			theText[i] = lines[i];
		}
		if (font == null)
			font = defaultFont;
		this.font = font;
		setAntiAlias(antialiasedText);
		setStrokeColor(getStrokeColor());
		createTextArea();
		updateBounds();
	}
	
	private void createTextArea() {
		textArea = new JTextArea(5, 20);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
		textArea.setOpaque(false);
		textArea.setForeground(ROIColor);
		textArea.setCaretColor(ROIColor);
		if(getText() == null || getText().length()==0) {
			textArea.setText(line1a);
		}else {
			textArea.setText(getText());
		}
		textArea.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				updateText();
			}
			@Override
			public void removeUpdate(DocumentEvent e) {
				updateText();
			}
			@Override
			public void changedUpdate(DocumentEvent e) {
				updateText();
			}
		});
		
		textPane = new JScrollPane(textArea);
		textPane.setName(getProperty(ContextKey.RoiID.name()));
		textPane.setOpaque(false);
		textPane.getViewport().setOpaque(false);
		
	}

    Font getScaledFont() {
        if (font==null)
            font = new Font("SansSerif", Font.PLAIN, 14);
        double mag = getMagnification();
        if (nonScalable || imp==null || mag==1.0)
            return font;
        else
            return font.deriveFont((float)(font.getSize()*mag*getComponentScaleFactor()[0]));
    }
    
    /** Renders the text on the image. Draws the text in
     * the foreground color if ip.setColor(Color) has
     * not been called.
     *  @see ij.process.ImageProcessor#setFont(Font)
     *  @see ij.process.ImageProcessor#setAntialiasedText(boolean)
     *  @see ij.process.ImageProcessor#setColor(Color)
    */
	public void drawPixels(ImageProcessor ip) {
		ip.setFont(font);
		ip.setAntialiasedText(getAntiAlias());
		FontMetrics metrics = ip.getFontMetrics();
		int fontHeight = metrics.getHeight();
		int i = 0;
		int yy = 0;
		int xi = (int) Math.round(getXBase());
		int yi = (int) Math.round(getYBase());

		String[] lines = Tools.split(textArea.getText(), "\n");
		if (lines == null || lines.length == 0) {
			Log.logger.fine("TextRoi, Can not draw, because text is null.");
			return;
		}
		
		while (i < MAX_LINES && lines[i] != null) {
			switch (justification) {
			case LEFT:
				ip.drawString(lines[i], xi, yi + yy + fontHeight);
				break;
			case CENTER:
				int tw = metrics.stringWidth(lines[i]);
				ip.drawString(lines[i], xi + (this.width - tw) / 2, yi + yy + fontHeight);
				break;
			case RIGHT:
				tw = metrics.stringWidth(lines[i]);
				ip.drawString(lines[i], xi + this.width - tw, yi + yy + fontHeight);
				break;
			}
			i++;
			yy += fontHeight;
		}
	}

	/** Draws the text on the screen, clipped to the ROI. */
	public void draw(Graphics g) {
		int compW = slide.getWidth();
		int compH = slide.getHeight();
		if (prevCompW != compW || prevCompH != compH) {
			/*
			 * IMPORTANT
			 * update showing text state
			 */
			SwingUtilities.invokeLater(() -> {
				textArea.repaint();
				textPane.revalidate();
				textPane.repaint();
				updateBounds();
			});
		}
		super.draw(g); // draw the rectangle
		if (initialize) {
			if (textPane != null && textPane.isShowing()) {
				/*
				 * update showing text state
				 */
				SwingUtilities.invokeLater(() -> {
					textArea.repaint();
					textPane.revalidate();
					textPane.repaint();
					updateBounds();
				});
				initialize = false;
			}
		}
	}
    
    public void requestFocusInWindow() {
    	textArea.requestFocusInWindow();
    }
    
    public void drawOverlay(Graphics g) {
        drawText(g);
    }

	void drawText(Graphics g) {
		Color color = strokeColor != null ? strokeColor : ROIColor;
		if (isActiveOverlayRoi()) {
			color = Color.cyan;
		}
		Java2.setAntialiasedText(g, getAntiAlias());
		double mag = getMagnification();
		double[] scaleXY = getComponentScaleFactor();
		int xi = (int) Math.round(getXBase());
		int yi = (int) Math.round(getYBase());
		double widthd = bounds != null ? bounds.width : this.width;
		double heightd = bounds != null ? bounds.height : this.height;
		int widthi = (int) Math.round(widthd);
		int heighti = (int) Math.round(heightd);
		Font font = getScaledFont();
		FontMetrics metrics = g.getFontMetrics(font);
		int fontHeight = metrics.getHeight();
		int descent = metrics.getDescent();
		g.setFont(font);
		Graphics2D g2d = (Graphics2D) g;
		int sx = nonScalable ? xi : screenX((int) getXBase());
		int sy = nonScalable ? yi : screenY((int) getYBase());
		int sw = nonScalable ? widthi : (int) (mag * scaleXY[0] * widthd);
		int sh = nonScalable ? heighti : (int) (mag * scaleXY[1] * heightd);
		AffineTransform at = null;
		if (angle != 0.0) {
			at = g2d.getTransform();
			double cx = sx, cy = sy;
			double theta = Math.toRadians(angle);
			g2d.rotate(-theta, cx, cy);
		}
		int i = 0;
		if (fill) {
			//int alpha = fillColor.getAlpha();
			g.setColor(fillColor);
			g.fillRect(sx, sy, sw, sh);
		}
		g.setColor(color);
		while (i < MAX_LINES && theText[i] != null) {
			switch (justification) {
			case LEFT:
				g.drawString(theText[i], sx, sy + fontHeight - descent);
				break;
			case CENTER:
				int tw = metrics.stringWidth(theText[i]);
				g.drawString(theText[i], sx + (sw - tw) / 2, sy + fontHeight - descent);
				break;
			case RIGHT:
				tw = metrics.stringWidth(theText[i]);
				g.drawString(theText[i], sx + sw - tw, sy + fontHeight - descent);
				break;
			}
			i++;
			sy += fontHeight;
		}
		if (at != null) // restore transformation matrix used to rotate text
			g2d.setTransform(at);
	}

    /** Returns the name of the default font. Use getCurrentFont().getName()
         to get the name of the font that this TextRoi is using. */
    public static String getDefaultFontName() {
        return name;
    }

    /** Returns the default font size. Use getCurrentFont().getSize()
         to get the size of the font that this TextRoi is using. */
    public static int getDefaultFontSize() {
        return size;
    }

    /** Returns the default font style. Use getCurrentFont().getStyle()
         to get the style of the font that this TextRoi is using. */
    public static int getDefaultFontStyle() {
        return style;
    }
    
    /** Sets the current font. */
    public void setFont(Font font) {
        this.font = font;
        updateBounds();
    }
    
    /** Sets the size of the current font. */
    public void setFontSize(int size) {
        if (font==null)
            font = defaultFont;
        font = font.deriveFont((float)size);
    }
        
    /** Returns the current font. */
    public Font getCurrentFont() {
        return font;
    }
    
    /** Returns the state of the global 'antialiasedText' variable, which is used by the "Fonts" widget. */
    public static boolean isAntialiased() {
        return antialiasedText;
    }
    
    @Override
    public void setSlideGlass(SlideGlass sg) {
    	super.setSlideGlass(sg);
    	if(sg != null && textPane != null) {
    		CanvasGlass cg = (CanvasGlass)sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
    		textPane.setName(getProperty(ContextKey.RoiID.name()));
			/*
			 * If the same object is added multiple times, only one of them will exist in
			 * the Swing container and will appear at the position where it was last added.
			 */
    		cg.add(textPane);
    	}
    }

    /** Sets the state of the global 'antialiasedText' variable. */
    public static void setAntialiasedText(boolean antialiased) {
        antialiasedText = antialiased;
    }
    
    /** Sets the 'antiAlias' instance variable. */
    public void setAntialiased(boolean antiAlias) {
        setAntiAlias(antiAlias);
        if (angle>0.0)
            setAntiAlias(true);
    }
    
    /** Returns the state of the 'antiAlias' instance variable. */
    public boolean getAntialiased() {
        return getAntiAlias();
    }
        
	/** Sets the default text tool justification (LEFT, CENTER or RIGHT). */
	public void setGlobalJustification(int justification) {
		if (justification < 0 || justification > RIGHT)
			justification = LEFT;
		globalJustification = justification;
		if (slide != null) {
			slide.repaint();
		}
	}
    
    /** Returns the default text tool justification (LEFT, CENTER or RIGHT). */
    public static int getGlobalJustification() {
        return globalJustification;
    }

	/** Sets the 'justification' instance variable (LEFT, CENTER or RIGHT) */
	public void setJustification(int justification) {
		if (justification < 0 || justification > RIGHT)
			justification = LEFT;
		this.justification = justification;
		updateBounds();
		if (slide != null) {
			slide.repaint();
		}
	}
    
    /** Returns the value of the 'justification' instance variable (LEFT, CENTER or RIGHT). */
    public int getJustification() {
        return justification;
    }

    /** Sets the global font face, size and style that will be used by
        TextROIs interactively created using the text tool. */
    public void setFont(String fontName, int fontSize, int fontStyle) {
        setFont(fontName, fontSize, fontStyle, true);
    }
    
    /** Sets the font face, size, style and antialiasing mode that will 
        be used by TextROIs interactively created using the text tool. */
    public void setFont(String fontName, int fontSize, int fontStyle, boolean antialiased) {
        name = fontName;
        size = fontSize;
        style = fontStyle;
        globalJustification = LEFT;
        antialiasedText = antialiased;
        setFont(new Font(name, style, size));
    }

    /** Sets the default font. */
    public static void setDefaultFont(Font font) {
        defaultFont = font;
    }
    
    /** Sets the default font size. */
    public static void setDefaultFontSize(int size) {
        defaultFont = defaultFont.deriveFont((float)size);
    }

    /** Sets the default fill (background) color. */
    public static void setDefaultFillColor(Color fillColor) {
        defaultFillColor = fillColor;
    }
    
    public void mouseDrag(int sx, int sy, int flags) {
		super.mouseDrag(sx, sy, flags);
		updateBounds();
	}
    
	public void mouseWheelMoved(MouseEvent e) {
		if (textPane.getBounds().contains(e.getPoint())) {
			textPane.dispatchEvent(SwingUtilities.convertMouseEvent((Component) e.getSource(), e, textPane));
		}
	}

	public void handleMouseUp(int screenX, int screenY) {
		super.handleMouseUp(screenX, screenY);
		updateBounds();
		setFocusable(true);
	}
    
	/**
	 * Increases the size of bounding rectangle so it's large enough to hold the
	 * text.
	 */
	private void updateBounds() {
		Font font = getScaledFont();
		Graphics g = getFontGraphics(font);
		Java2.setAntialiasedText(g, getAntiAlias());
		Rectangle2D.Double b = getFloatBounds();
		setBounds(b);
		if (slide != null) {
			prevCompW = slide.getWidth();
			prevCompH = slide.getHeight();
		}
	}
    
    @Override
	public void setBounds(Rectangle2D.Double b) {
		super.setBounds(b);
		/*
		 * set TextArea&Pane bounds.
		 */
		if (slide != null && textPane != null && textPane.isVisible()) {
			textPane.setBounds((int)getXBase(), (int)getYBase(), width, height);
			CanvasGlass cg = (CanvasGlass) slide.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
			cg.add(textPane);// move to current location
			slide.repaint();
		}
	}
    
    private Graphics getFontGraphics(Font font) {
        if (fontGraphics==null) {
            BufferedImage bi =new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            fontGraphics = (Graphics2D)bi.getGraphics();
        }
        fontGraphics.setFont(font);
        return  fontGraphics;
    }
    
    void updateText() {
    	if(textArea != null) {
			setProperty(ContextKey.Description.name(), textArea.getText());
			updateClipRect();
    	}
    }

    double stringWidth(String s, FontMetrics metrics, Graphics g) {
        java.awt.geom.Rectangle2D r = metrics.getStringBounds(s, g);
        return r.getWidth();
    }
    
    /** Used by the Recorder for recording the text tool. */
    public String getMacroCode(String cmd, ImagePlus imp) {
        String code = "";
        boolean script = Recorder.scriptMode();
        boolean addSelection = cmd.startsWith("Add");
        if (script && !addSelection)
            code += "ip = imp.getProcessor();\n";
        if (script) {
            String str = "Font.PLAIN";
            if (style==Font.BOLD)
                str =  "Font.BOLD";
            else if (style==Font.ITALIC)
                str =  "Font.ITALIC";
            code += "font = new Font(\""+name+"\", "+str+", "+size+");\n";
            if (addSelection)
                return getAddSelectionScript(code);
            code += "ip.setFont(font);\n";
        } else {
            String options = "";
            if (style==Font.BOLD)
                options += "bold";
            if (style==Font.ITALIC)
                options += " italic";
            if (antialiasedText)
                options += " antialiased";
            if (options.equals(""))
                options = "plain";
            code += "setFont(\""+name+"\", "+size+", \""+options+"\");\n";
        }
        ImageProcessor ip = imp.getProcessor();
        ip.setFont(new Font(name, style, size));
        FontMetrics metrics = ip.getFontMetrics();
        int fontHeight = metrics.getHeight();
        if (script)
            code += "ip.setColor(new Color("+getColorArgs(getStrokeColor())+"));\n";
        else
            code += "setColor(\""+Colors.colorToString(getStrokeColor())+"\");\n";
        if (addSelection) {
            code += "Overlay.drawString(\""+text()+"\", "+this.x+", "+(this.y+fontHeight)+", "+getAngle()+");\n";
            code += "Overlay.show();\n";
        } else {
            code += (script?"ip.":"")+"drawString(\""+text()+"\", "+this.x+", "+(this.y+fontHeight)+");\n";
            if (script)
                code += "imp.updateAndDraw();\n";
            else
                code += "//makeText(\""+text()+"\", "+this.x+", "+(this.y+fontHeight)+");\n";
        }
        return (code);
    }
    
    private String text() {
        String text = "";
        for (int i=0; i<MAX_LINES; i++) {
            if (theText[i]==null) break;
            text += theText[i];
            if (theText[i+1]!=null) text += "\\n";
        }
        return text;
    }
    
    private String getAddSelectionScript(String code) {
        code += "roi = new TextRoi("+this.x+", "+this.y+", \""+text()+"\", font);\n";
        code += "roi.setStrokeColor(new Color("+getColorArgs(getStrokeColor())+"));\n";
        if (getFillColor()!=null)
            code += "roi.setFillColor(new Color("+getColorArgs(getFillColor())+"));\n";
        int just = getJustification();
        if (just>LEFT) {
            if (just==CENTER)
                code += "roi.setJustification(TextRoi.CENTER);\n";
            else if (just==RIGHT)
                code += "roi.setJustification(TextRoi.RIGHT);\n";
        }
        if (getAngle()!=0.0)
            code += "roi.setAngle("+getAngle()+");\n";
        code += "overlay.add(roi);\n";
        return code;
    }
    
    private String getColorArgs(Color c) {
        return IJ.d2s(c.getRed()/255.0,2)+", "+IJ.d2s(c.getGreen()/255.0,2)+", "+IJ.d2s(c.getBlue()/255.0,2);
    }
    
    public String getText() {
        String txt = getProperty(ContextKey.Description.name());
        return txt;
    }
    
    public boolean isDrawingTool() {
        return true;
    }
    
    public void clear(ImageProcessor ip) {
        if (font==null)
            ip.fill();
        else {
            ip.setFont(font);
            ip.setAntialiasedText(antialiasedText);
            int i=0, w=0;
            while (i<MAX_LINES && theText[i]!=null) {
                int w2 = ip.getStringWidth(theText[i]);
                if (w2>w)
                    w = w2;
                i++;
            }
            Rectangle r = ip.getRoi();
            if (w>r.width) {
                r.width = w;
                ip.setRoi(r);
            }
            ip.fill();
        }
    }

    @Override
    public void setLocation(int x, int y) {
        super.setLocation(x, y);
        oldWidth = this.width;
    }

    /** Returns a copy of this TextRoi. */
    public synchronized Object clone() {
        TextRoi tr = (TextRoi)super.clone();
        tr.theText = new String[MAX_LINES];
        for (int i=0; i<MAX_LINES; i++)
            tr.theText[i] = theText[i];
        return tr;
    }
    
    public double getAngle() {
        return angle;
    }
    
    public void setAngle(double angle) {
        this.angle = angle;
        if (angle!=0.0)
            setAntiAlias(true);
    }

    public boolean getDrawStringMode() {
        return false;
    }
    
    public void setDrawStringMode(boolean drawStringMode) {
    }
    
    public void setPreviousTextRoi(RoiObj previousRoi) {
        TextRoi.previousRoi = previousRoi;
    }
    
    public void setFocusable(boolean enable) {
    	if(enable) {
    		textArea.setFocusable(true);
    		textArea.requestFocusInWindow();
    	}else {
    		textArea.setFocusable(false);
    		((EventGlass)slide.getGlassAt(SlideGlass.EVENT_LAYER)).requestFocusInWindow();
    	}
    }
    
    public boolean isFocusable() {
    	return textArea.isFocusable();
    }
    
    /** @deprecated Replaced by getDefaultFontName */
    public static String getFont() {
        return name;
    }

    /** @deprecated Replaced by getDefaultFontSize */
    public static int getSize() {
        return size;
    }

    /** @deprecated Replaced by getDefaultFontStyle */
    public static int getStyle() {
        return style;
    }
}
