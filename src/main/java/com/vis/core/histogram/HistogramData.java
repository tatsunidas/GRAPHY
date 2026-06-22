/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.histogram;

/**
 * Result of a histogram analysis: the binned counts plus the standard
 * first-order statistics computed from the same (calibrated, when
 * available) values.
 */
public final class HistogramData {

	public final double binStart; // value at the left edge of bin 0
	public final double binWidth;
	public final int binCount;
	public final long[] counts; // length == binCount

	public final long totalCount;
	public final double min;
	public final double max;
	public final double mean;
	public final double stdDev;
	public final double variance;
	public final double mode; // bin-center of the most frequent bin
	public final double median; // linearly interpolated from cumulative bin counts
	public final double skewness;
	public final double kurtosis; // excess kurtosis (normal distribution == 0)
	public final double entropy; // Shannon entropy, base 2, over non-empty bins
	public final String valueUnit; // e.g. "HU", or "raw" when uncalibrated

	HistogramData(double binStart, double binWidth, int binCount, long[] counts, long totalCount, double min,
			double max, double mean, double stdDev, double variance, double mode, double median, double skewness,
			double kurtosis, double entropy, String valueUnit) {
		this.binStart = binStart;
		this.binWidth = binWidth;
		this.binCount = binCount;
		this.counts = counts;
		this.totalCount = totalCount;
		this.min = min;
		this.max = max;
		this.mean = mean;
		this.stdDev = stdDev;
		this.variance = variance;
		this.mode = mode;
		this.median = median;
		this.skewness = skewness;
		this.kurtosis = kurtosis;
		this.entropy = entropy;
		this.valueUnit = valueUnit;
	}

	public double binLow(int index) {
		return binStart + index * binWidth;
	}

	public double binHigh(int index) {
		return binStart + (index + 1) * binWidth;
	}

	public double binCenter(int index) {
		return binStart + (index + 0.5) * binWidth;
	}

	public long maxBinCount() {
		long max = 0;
		for (long c : counts) {
			if (c > max) max = c;
		}
		return max;
	}
}
