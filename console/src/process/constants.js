// @flow

// date fields
export const dateKeys = ["lastUpdatedAt", "createdAt"];

export const idKey = "instanceId";
export const statusKey = "status";

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
