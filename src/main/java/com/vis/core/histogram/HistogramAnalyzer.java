/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.histogram;

import java.util.Collections;
import java.util.List;

import ij.measure.Calibration;
import ij.process.ImageProcessor;

/**
 * Computes a user-configurable (bin width or bin count) histogram plus the
 * standard first-order statistics, from either a single slice or a whole
 * stack of slices. Values are read through {@link Calibration#getCValue}
 * when a value calibration is available (e.g. CT HU), otherwise raw pixel
 * values are used directly - same policy already used for the 3D LUT
 * histogram and Curved MPR earlier in this project.
 */
public class HistogramAnalyzer {

	private HistogramAnalyzer() {
	}

	public static HistogramData analyzeSlice(ImageProcessor ip, Calibration cal, BinSpec spec) {
		return analyze(Collections.singletonList(ip), cal, spec);
	}

	public static HistogramData analyze(List<ImageProcessor> slices, Calibration cal, BinSpec spec) {
		boolean calibrated = cal != null && cal.calibrated();
		String unit = calibrated ? cal.getValueUnit() : "raw";

		// Pass 1: min/max/mean.
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		double sum = 0;
		long count = 0;
		for (ImageProcessor ip : slices) {
			int w = ip.getWidth(), h = ip.getHeight();
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					double v = calibratedValue(ip, x, y, cal, calibrated);
					if (v < min) min = v;
					if (v > max) max = v;
					sum += v;
					count++;
				}
			}
		}
		if (count == 0) {
			throw new IllegalArgumentException("No pixels to analyze");
		}
		double mean = sum / count;
		double range = Math.max(max - min, 1e-9);

		double binWidth;
		int binCount;
		if (spec.mode == BinSpec.Mode.BIN_WIDTH) {
			binWidth = Math.max(spec.value, 1e-9);
			binCount = Math.max(1, (int) Math.ceil(range / binWidth));
		} else {
			binCount = Math.max(1, (int) Math.round(spec.value));
			binWidth = range / binCount;
		}
		double binStart = min;
		long[] counts = new long[binCount];

		// Pass 2: central moments (for stdDev/skewness/kurtosis) and bin counts.
		double sumSq = 0, sumCube = 0, sumQuad = 0;
		for (ImageProcessor ip : slices) {
			int w = ip.getWidth(), h = ip.getHeight();
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					double v = calibratedValue(ip, x, y, cal, calibrated);
					double d = v - mean;
					double d2 = d * d;
					sumSq += d2;
					sumCube += d2 * d;
					sumQuad += d2 * d2;

					int bin = (int) Math.floor((v - binStart) / binWidth);
					if (bin < 0) bin = 0;
					if (bin >= binCount) bin = binCount - 1;
					counts[bin]++;
				}
			}
		}

		double variance = sumSq / count;
		double stdDev = Math.sqrt(variance);
		double skewness = stdDev > 0 ? (sumCube / count) / Math.pow(stdDev, 3) : 0;
		double kurtosis = stdDev > 0 ? (sumQuad / count) / Math.pow(stdDev, 4) - 3.0 : 0; // excess kurtosis

		int modeBin = 0;
		long modeCount = -1;
		for (int i = 0; i < binCount; i++) {
			if (counts[i] > modeCount) {
				modeCount = counts[i];
				modeBin = i;
			}
		}
		double mode = binStart + (modeBin + 0.5) * binWidth;

		double median = estimateMedian(counts, binStart, binWidth, count);
		double entropy = shannonEntropy(counts, count);

		return new HistogramData(binStart, binWidth, binCount, counts, count, min, max, mean, stdDev, variance, mode,
				median, skewness, kurtosis, entropy, unit);
	}

	/** Boolean mask (row-major, width*height) flagging pixels of this single slice whose calibrated value falls in [lo, hi). */
	public static boolean[] computeBinMask(ImageProcessor ip, Calibration cal, double lo, double hi) {
		boolean calibrated = cal != null && cal.calibrated();
		int w = ip.getWidth(), h = ip.getHeight();
		boolean[] mask = new boolean[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				double v = calibratedValue(ip, x, y, cal, calibrated);
				mask[y * w + x] = v >= lo && v < hi;
			}
		}
		return mask;
	}

	private static double calibratedValue(ImageProcessor ip, int x, int y, Calibration cal, boolean calibrated) {
		float raw = ip.getf(x, y);
		return calibrated ? cal.getCValue(raw) : raw;
	}

	private static double estimateMedian(long[] counts, double binStart, double binWidth, long total) {
		long half = total / 2;
		long cumulative = 0;
		for (int i = 0; i < counts.length; i++) {
			long next = cumulative + counts[i];
			if (next >= half) {
				double frac = counts[i] > 0 ? (half - cumulative) / (double) counts[i] : 0;
				return binStart + (i + frac) * binWidth;
			}
			cumulative = next;
		}
		return binStart + counts.length * binWidth;
	}

	private static double shannonEntropy(long[] counts, long total) {
		double entropy = 0;
		double log2 = Math.log(2);
		for (long c : counts) {
			if (c <= 0) continue;
			double p = (double) c / total;
			entropy -= p * (Math.log(p) / log2);
		}
		return entropy;
	}
}
