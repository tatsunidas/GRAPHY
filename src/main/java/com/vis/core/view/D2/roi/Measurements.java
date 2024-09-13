/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of graphy, hosted at https://github.com/graphy.
 *
 * The Initial Developer of the Original Code is
 * Visionary Imaging Services, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK *****
 */
package com.vis.core.view.D2.roi;

/**
 * 
 * @author tatsunidas
 *
 */
public enum Measurements {
	/**
	 * Measurements
	 * see, ij.measure.Measurements
	 */
	AREA(1),
	MEAN(2),
	STD_DEV(4),
	MODE(8),
	MIN_MAX(16),
	MIN(17),
	MAX(18),
	CENTROID(32),
	CENTROID_X(33),
	CENTROID_Y(34),
	CENTER_OF_MASS(64),
	CENTER_OF_MASS_X(65),
	CENTER_OF_MASS_Y(66),
	PERIMETER(128),
	ROI_X(200),
	ROI_Y(201),
	ROI_WIDTH(202),
	ROI_HEIGHT(203),
	MAJOR(204),
	MINOR(205),
	ANGLE(206),
	LENGTH(207),
	LIMIT(256),/*limit to threshold*/
	MIN_THRESHOLD(257),
	MAX_THRESHOLD(258),
	RECT(512),
	LABELS(1024),
	ELLIPSE(2048),
	INVERT_Y(4096),
	CIRCULARITY(8192),SHAPE_DESCRIPTORS(8192),
	ROUNDNESS(8193),
	SOLIDITY(8194),
	FERET(16384),
	FERET_X(16385),
	FERET_Y(16386),
	FERET_ANGLE(16387),
	FERET_MIN(16388),
	INTEGRATED_DENSITY(0x8000),
	RAW_INTEGRATED_DENSITY(0x0801),
	MEDIAN(0x10000),
	SKEWNESS(0x20000),
	KURTOSIS(0x40000),
	AREA_FRACTION(0x80000),
	SLICE(0x100000), STACK_POSITION(0x100000),
	CHANNEL(0x10001),
	GROUP(0x10002),
	ASPECT_RATIO(0x10002),
	SCIENTIFIC_NOTATION(0x200000),
	ADD_TO_OVERLAY(0x400000),
	NaN_EMPTY_CELLS(0x800000);
	
	private final int id;
	private Measurements(int id) {
		this.id = id;
	}
	
	public int id() {
		return id;
	}
	
	/** All measurement options */
	public static int allStats() {
		int all_stats = 0;
		for(Measurements m : values()) {
			all_stats += m.id;
		}
		return all_stats;
	}
}
