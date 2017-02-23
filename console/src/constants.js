// @flow
import type {SortDirection, FetchRange} from "./types";
import {sort as sortConstants} from "./types";

export const sort = sortConstants;

export const reverseSort = (dir: SortDirection): SortDirection => dir === sort.ASC ? sort.DESC : sort.ASC;

export const process = {
    runningStatus: "RUNNING",
    failedStatus: "FAILED",
    finishedStatus: "FINISHED",
    startingStatus: "STARTING"
};

export const history = {
    columns: [
        {key: "instanceId", label: "Instance ID", collapsing: true},
        {key: "status", label: "Status"},
        {key: "initiator", label: "Initiator"},
        {key: "lastUpdateDt", label: "Updated"},
        {key: "createdDt", label: "Created"},
        {key: "actions", label: "Actions", collapsing: true}
    ],

    statusToIcon: {
        "STARTING": "hourglass start",
        "RUNNING": "hourglass half",
        "FINISHED": "checkmark",
        "FAILED": "remove"
    },

    idKey: "instanceId",
    sortableKeys: ["status", "initiator", "lastUpdateDt", "createdDt"],
    actionsKey: "actions",
    logFileNameKey: "logFileName",
    logLinkKey: "instanceId",
    dateKeys: ["lastUpdateDt", "createdDt"],
    statusKey: "status",

    canBeKilledStatuses: [process.runningStatus],
    failedStatuses: [process.failedStatus],

    defaultSortKey: "lastUpdateDt",
    defaultSortDir: sort.DESC
};

export const projectList = {
    columns: [
        {key: "name", label: "Name", collapsing: true},
        {key: "templates", label: "Templates"},
        {key: "actions", label: "Actions", collapsing: true}
    ],

    nameKey: "name",
    templatesKey: "templates",
    sortableKeys: ["name"],
    actionsKey: "actions",

    defaultSortKey: "name",
    defaultSortDir: sort.ASC
};

export const templateList = {
    nameKey: "name"
};

export const log = {
    fetchIncrement: 2048,
    fetchDelay: 5000,
    defaultFetchRange: ({low: undefined, high: 2048}: FetchRange)
};