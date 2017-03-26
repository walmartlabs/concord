// @flow
import type {SortDirection} from "../types";
import {sort as sortConstants} from "../types";

export const title = "Concord";

export const sort = sortConstants;

export const reverseSort = (dir: SortDirection): SortDirection => dir === sort.ASC ? sort.DESC : sort.ASC;

export const process = {
    status: {
        runningStatus: "RUNNING",
        startingStatus: "STARTING",
        suspendedStatus: "SUSPENDED",
        resumingStatus: "RESUMING",
        finishedStatus: "FINISHED",
        failedStatus: "FAILED"
    },

    statusIcons: {
        "STARTING": "hourglass start",
        "RUNNING": "hourglass half",
        "SUSPENDED": "wait",
        "RESUMING": "hourglass end",
        "FINISHED": "checkmark",
        "FAILED": "remove"
    },

    statusColors: {
        "STARTING": "grey",
        "RUNNING": "grey",
        "SUSPENDED": "blue",
        "RESUMING": "grey",
        "FINISHED": "green",
        "FAILED": "red"
    }
};

