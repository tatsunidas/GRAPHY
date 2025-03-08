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
package com.vis.core.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.vis.core.log.Log;

/**
 * 
 * @author tatsunidas
 *
 */
public class TaskManager {
	
	private final Map<Integer, Task> tasks = new ConcurrentHashMap<>();
    private final AtomicInteger taskIdCounter = new AtomicInteger(0);
	
	private static TaskManager tm = new TaskManager();
	
	public static TaskManager getInstance() {
		return tm;
	}
	
	/**
	 * 
	 * @param t
	 * @return task id
	 */
	public int addTask(Task t) {
		int taskId = taskIdCounter.incrementAndGet();
		tasks.put(taskId, t);
		return taskId;
	}
	
	public void startTask(int taskId) {
        Task task = tasks.get(taskId);
        if (task != null) {
            task.start();
            task.monitorTasks();
        }
    }

    public void pauseTask(int taskId) {
        Task task = tasks.get(taskId);
        if (task != null) {
            task.setSuspended(true);
        }
    }

    public void resumeTask(int taskId) {
        Task task = tasks.get(taskId);
        if (task != null) {
        	task.setSuspended(false);
        }
    }

    public void removeTask(int taskId) {
        Task task = tasks.get(taskId);
        if (task != null) {
            task.setStopped(true);
            tasks.remove(taskId);
        }
    }

    public boolean isTaskCompleted(int taskId) {
        Task task = tasks.get(taskId);
        return task == null || task.isCompleted();
    }

    public List<Integer> getAllTaskIds() {
        return new ArrayList<>(tasks.keySet());
    }

    public void removeCompletedTasks() {
        tasks.entrySet().removeIf(entry -> entry.getValue().isCompleted());
    }

	public Task getTask(int taskID) {
		return tasks.get(taskID);
	}
	
	public void shutdownAndWait() {
		List<Integer> taskIds = getAllTaskIds();
		while (!taskIds.isEmpty()) {
			taskIds.removeIf(this::isTaskCompleted);
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		Log.logger.info("All tasks completed.");
	}
	
	public Thread getThread(long tid) {
		for (Thread t : Thread.getAllStackTraces().keySet()) {
			if (tid == t.getId()) {
				return t;
			}
		}
		return null;
	}
	
	public int size() {
		return tasks.size();
	}
	
}
