/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */
// @flow

// date fields
export const dateKeys = ["lastUpdatedAt", "createdAt"];

export const idKey = "instanceId";
export const statusKey = "status";
export const projectName = "projectName";

export const failedStatuses = ["FAILED"];

// statuses in which a process can be killed
export const canBeKilledStatuses = ["RUNNING", "SUSPENDED"];

// statuses that can have a log file
export const hasLogStatuses = ["STARTING", "RUNNING", "SUSPENDED", "RESUMING", "FINISHED", "FAILED", "CANCELLED"];

// statuses that hava a process state
export const hasProcessState = ["RUNNING", "SUSPENDED", "RESUMING", "FINISHED", "FAILED", "CANCELLED"];

export const status = {
    preparingStatus: "PREPARING",
    enqueuedStatus: "ENQUEUED",
    runningStatus: "RUNNING",
    startingStatus: "STARTING",
    suspendedStatus: "SUSPENDED",
    resumingStatus: "RESUMING",
    finishedStatus: "FINISHED",
    failedStatus: "FAILED"
};

export const statusIcons = {
    "PREPARING": "hourglass empty",
    "STARTING": "hourglass start",
    "RUNNING": "hourglass half",
    "SUSPENDED": "wait",
    "RESUMING": "hourglass end",
    "FINISHED": "checkmark",
    "FAILED": "remove",
    "CANCELLED": "cancel"
};

export const statusColors = {
    "PREPARING": "grey",
    "STARTING": "grey",
    "RUNNING": "grey",
    "SUSPENDED": "blue",
    "RESUMING": "grey",
    "FINISHED": "green",
    "FAILED": "red"
};

export const activeStatuses = [status.preparingStatus, status.enqueuedStatus, status.runningStatus, status.startingStatus, status.resumingStatus];
export const finalStatuses = [status.finishedStatus, status.failedStatus];
