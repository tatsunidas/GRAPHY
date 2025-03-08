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
package com.vis.core.task.context;

import java.util.HashMap;
import com.vis.core.task.TaskContext;
import com.vis.core.task.TaskType;

/**
 * to show progressbar on treetable in study level.
 * @author tatsunidas
 *
 */
public class ImportingStateContext implements TaskContext{
	
	final long threadId;
	final int taskId;
	int currentIndex=0;//progress
	int total;
	final TaskType type;
	/*
	 * studyUid is used for unique key.
	 * if perform multiple importer tasks at same time,
	 * and these have same studyuid,
	 * randomly show progressbar in contexts.
	 */
	final String suid;
	HashMap<String, Object> con;
	
	public ImportingStateContext(String studyInstanceUID, HashMap<String, Object> con) {
		if(!validateContext(con)) {
			throw new IllegalArgumentException("TaskContext is not valid.");
		}
		this.suid = studyInstanceUID;
		this.con = con;
		this.type = (TaskType)con.get(TaskContext.TASK_TYPE);
		total = (Integer)con.get(TaskContext.SIZE);
		threadId = (Long)con.get(TaskContext.THREAD_ID);
		taskId = (Integer)con.get(TaskContext.TASK_ID);
	}
		
	public boolean validateContext(HashMap<String, Object> con) {
		if(con == null) {
			return false;
		}
		TaskType type = (TaskType) con.get(TaskContext.TASK_TYPE);
		if(type == null) {
			return false;
		}
		if(con.get(TaskContext.THREAD_ID) == null) {
			return false;
		}
		return true;
	}
	
	public String getStudyUID() {
		return suid;
	}
	
	@Override
	public void updateState(HashMap<String, Object> updated_con) {
		for(String key : updated_con.keySet()) {
			Object obj = updated_con.get(key);
			this.con.put(key, obj);
			if(key.equals(TaskContext.CURRENT_IND)) {
				currentIndex = (Integer)obj;
			}
		}
	}

	@Override
	public int currentIndex() {
		return currentIndex;
	}

	@Override
	public int totalSize() {
		return total;
	}
	
	public TaskType getType() {
		return type;
	}

	@Override
	public long getThreadId() {
		return threadId;
	}
	
	public int getTaskId() {
		return taskId;
	}
}
