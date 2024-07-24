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
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), available at http://sourceforge.net/projects/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * TIANI Medgraph AG.
 * Portions created by the Initial Developer are Copyright (C) 2003-2005
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * Gunter Zeilinger <gunter.zeilinger@tiani.com>
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
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chex.cdw.mbean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dcm4che.data.Dataset;
import org.dcm4che.dict.Tags;

/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 4324 $ $Date: 2006-10-12 22:29:12 +0200 (Do, 12. Okt 2006) $
 * @since 19.07.2004
 *
 */
class FilesetComponent implements Comparable {

    private static final String[] PROMPTS = { "File-set", "Patient", "Study",
            "Series", "Instance"};

    public static final int ROOT = 0;

    public static final int PATIENT = 1;

    public static final int STUDY = 2;

    public static final int SERIES = 3;

    public static final int INSTANCE = 4;

    public static final int ASC = 1;

    public static final int DESC = -1;
    
    private final String id;

    private final String comparable;
    
    private final int order;

    private FilesetComponent parent = null;

    private ArrayList childs = null;

    private long size = 0L;

    private final String filePath;

    private final int level;

    public final long size() {
        return size;
    }

    public final int level() {
        return level;
    }

    public final FilesetComponent root() {
        return parent != null ? parent.root() : this;
    }

    public final FilesetComponent parent() {
        return parent;
    }

    public final List childs() {
        return Collections.unmodifiableList(childs);
    }

    public final String id() {
        return id;
    }

    private static String padWithLeadingZeros(String s) {
        return "0000000000".substring(Math.min(10, s.length())) + s;
    }

    public static FilesetComponent makeRootFilesetComponent() {
        return new FilesetComponent(null, null, ASC, "", ROOT);
    }

    public static FilesetComponent makePatientFilesetComponent(Dataset ds,
            String filePath) {
        return new FilesetComponent(ds.getString(Tags.PatientID), ds.getString(
                Tags.PatientName, ""), ASC, filePath, PATIENT);
    }

    public static FilesetComponent makeStudyFilesetComponent(Dataset ds,
            boolean putNewestStudyOnFirstMedia, String filePath) {
        return new FilesetComponent(ds.getString(Tags.StudyInstanceUID), ds
                .getString(Tags.StudyDate, "")
                + ds.getString(Tags.StudyTime, ""), 
                putNewestStudyOnFirstMedia ? DESC : ASC, filePath, STUDY);
    }

    public static FilesetComponent makeSeriesFilesetComponent(Dataset ds,
            String filePath) {
        return new FilesetComponent(ds.getString(Tags.SeriesInstanceUID), ds
                .getString(Tags.Modality, "")
                + padWithLeadingZeros(ds.getString(Tags.SeriesNumber, "")), ASC,
                filePath, SERIES);
    }

    public static FilesetComponent makeInstanceFilesetComponent(Dataset ds,
            String filePath) {
        return new FilesetComponent(ds.getString(Tags.SOPInstanceUID),
                padWithLeadingZeros(ds.getString(Tags.InstanceNumber, "")), ASC,
                filePath, INSTANCE);
    }

    public String toString() {
        return PROMPTS[level]
                + "[id="
                + id
                + ", size="
                + size
                + (level < INSTANCE ? (", #" + PROMPTS[level + 1] + "=" + childs
                        .size())
                        : "") + "]";
    }

    private FilesetComponent(String id, String comparable, int order,
            String filePath, int level) {
        this.id = id;
        this.comparable = comparable;
        this.order = order;
        this.filePath = filePath;
        this.level = level;
    }

    public boolean isEmpty() {
        return size == 0L;
    }

    public int compareTo(Object o) {
        return comparable.compareTo(((FilesetComponent) o).comparable) * order;
    }

    public void addChild(FilesetComponent child) {
        if (child.parent != null)
                throw new IllegalStateException("Has already a parent: "
                        + child);
        if (childs == null) childs = new ArrayList();
        childs.add(child);
        incSize(child.size);
        child.parent = this;
    }

    public void removeChild(FilesetComponent child) {
        if (child.parent != this)
                throw new IllegalStateException("Not a child: " + child);
        childs.remove(child);
        incSize(-child.size);
        child.parent = null;
    }
    
    public void incSize(long delta) {
        this.size += delta;
        if (parent != null) parent.incSize(delta);
    }

    public FilesetComponent takeChilds(long maxSize) {
        FilesetComponent dest = newFilesetComponent();
        Collections.sort(childs);
        for (int i = 0; i < childs.size();) {
            FilesetComponent child = (FilesetComponent) childs.get(0);
            if (dest.size + child.size <= maxSize) {
                removeChild(child);
                dest.addChild(child);
            } else if (level == STUDY) { // don't preserve order of series
                ++i;
            } else {
                break;
            }
        }
        return dest;
    }

    private FilesetComponent newFilesetComponent() {
        FilesetComponent result = new FilesetComponent(id, comparable,
                order, filePath, level);
        if (parent != null) parent.newFilesetComponent().addChild(result);
        return result;
    }

    public String getFilePath() {
        return filePath;
    }
}
