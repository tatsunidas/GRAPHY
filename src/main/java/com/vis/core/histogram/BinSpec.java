/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.histogram;

/** How histogram bins are sized: a fixed bin width, or a fixed bin count spanning the data range. */
public final class BinSpec {

	public enum Mode {
		BIN_WIDTH, BIN_COUNT
	}

	public final Mode mode;
	public final double value; // bin width (calibrated units) when mode==BIN_WIDTH, else bin count

	public BinSpec(Mode mode, double value) {
		this.mode = mode;
		this.value = value;
	}

	public static BinSpec ofWidth(double binWidth) {
		return new BinSpec(Mode.BIN_WIDTH, binWidth);
	}

	public static BinSpec ofCount(int binCount) {
		return new BinSpec(Mode.BIN_COUNT, binCount);
	}
}
