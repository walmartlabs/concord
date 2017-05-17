// @flow

// date fields
export const dateKeys = ["lastUpdatedAt", "createdAt"];

export const idKey = "instanceId";
export const statusKey = "status";

export const failedStatuses = ["FAILED"];

// statuses in which a process can be killed
export const canBeKilledStatuses = ["RUNNING", "SUSPENDED"];

export const status = {
    runningStatus: "RUNNING",
    startingStatus: "STARTING",
    suspendedStatus: "SUSPENDED",
    resumingStatus: "RESUMING",
    finishedStatus: "FINISHED",
    failedStatus: "FAILED"
};

export const statusIcons = {
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

export const activeStatuses = [status.runningStatus, status.startingStatus, status.resumingStatus];
