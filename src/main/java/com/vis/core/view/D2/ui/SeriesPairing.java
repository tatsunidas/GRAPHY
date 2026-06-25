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
 * ***** END LICENSE BLOCK *****
 */
package com.vis.core.view.D2.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.vis.core.view.D2.ui.ComparisonBoard.SeriesEntry;

/**
 * Default series pairing for the Comparison View. Reorders one study's series so
 * that series comparable to an anchor study line up at the same row index.
 * <p>
 * Attribute-based, DB-only (no pixel load): same Modality is required for a
 * match, then BodyPartExamined and SeriesDescription token overlap rank the
 * candidates. The result is greedy: each anchor row claims its best remaining
 * candidate, unmatched candidates are appended in their natural (SeriesNumber)
 * order.
 * <p>
 * Limitation: because each study column is a single {@link
 * com.vis.core.view.D2.ui.glasses.Eyepiece} that cannot hold blank middle rows,
 * gaps are compacted rather than preserved — matched series are pulled up in
 * anchor order. For same-protocol prior/current studies (the common case) this
 * yields exact row alignment; manual drag-and-drop refines the rest.
 *
 * @author tatsunidas
 */
public final class SeriesPairing {

	private SeriesPairing() {
	}

	/**
	 * @param anchor    the reference column's series, in display order (may be null/empty).
	 * @param candidate the new column's series, in natural order.
	 * @return candidate reordered to align with the anchor; never null.
	 */
	public static List<SeriesEntry> order(List<SeriesEntry> anchor, List<SeriesEntry> candidate) {
		List<SeriesEntry> result = new ArrayList<>();
		if (candidate == null) {
			return result;
		}
		if (anchor == null || anchor.isEmpty()) {
			result.addAll(candidate);
			return result;
		}
		List<SeriesEntry> remaining = new ArrayList<>(candidate);
		for (SeriesEntry a : anchor) {
			SeriesEntry best = null;
			int bestScore = 0;
			for (SeriesEntry c : remaining) {
				int s = score(a, c);
				if (s > bestScore) {
					bestScore = s;
					best = c;
				}
			}
			if (best != null) {
				result.add(best);
				remaining.remove(best);
			}
		}
		result.addAll(remaining); // unmatched, natural order
		return result;
	}

	private static int score(SeriesEntry a, SeriesEntry c) {
		if (a.modality == null || c.modality == null || !a.modality.equalsIgnoreCase(c.modality)) {
			return 0; // never pair across modalities
		}
		int s = 4; // same modality
		if (notBlank(a.bodyPart) && a.bodyPart.equalsIgnoreCase(c.bodyPart)) {
			s += 2;
		}
		s += 2 * sharedTokens(a.description, c.description);
		return s;
	}

	private static int sharedTokens(String d1, String d2) {
		Set<String> t1 = tokenize(d1);
		if (t1.isEmpty()) {
			return 0;
		}
		int shared = 0;
		for (String t : tokenize(d2)) {
			if (t1.contains(t)) {
				shared++;
			}
		}
		return shared;
	}

	private static Set<String> tokenize(String s) {
		Set<String> tokens = new HashSet<>();
		if (s == null) {
			return tokens;
		}
		for (String raw : s.toLowerCase().split("[^a-z0-9]+")) {
			if (raw.length() >= 2) {
				tokens.add(raw);
			}
		}
		return tokens;
	}

	private static boolean notBlank(String s) {
		return s != null && !s.trim().isEmpty();
	}
}
