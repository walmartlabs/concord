// @flow

// date fields
export const dateKeys = ["lastUpdatedAt", "createdAt"];

export const idKey = "instanceId";
export const statusKey = "status";

export const failedStatuses = ["FAILED"];

// statuses in which a process can be killed
export const canBeKilledStatuses = ["RUNNING", "SUSPENDED"];

// statuses that can have a log file
export const hasLogStatuses = ["RUNNING", "SUSPENDED", "RESUMING", "FINISHED", "FAILED"];

export const status = {
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
    "FAILED": "remove"
};

export const statusColors = {
    "STARTING": "grey",
    "RUNNING": "grey",
    "SUSPENDED": "blue",
    "RESUMING": "grey",
    "FINISHED": "green",
    "FAILED": "red"
};

export const activeStatuses = [status.enqueuedStatus, status.runningStatus, status.startingStatus, status.resumingStatus];
export const finalStatuses = [status.finishedStatus, status.failedStatus];
