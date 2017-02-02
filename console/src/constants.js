export const sort = {
    ASC: "ASC",
    DESC: "DESC"
};

export const reverseSort = (dir) => dir === sort.ASC ? sort.DESC : sort.ASC;

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
        {key: "buttons", label: "Actions", collapsing: true}
    ],

    statusToIcon: {
        "STARTING": "hourglass start",
        "RUNNING": "hourglass half",
        "FINISHED": "checkmark",
        "FAILED": "remove"
    },

    idKey: "instanceId",
    sortableKeys: ["status", "initiator", "lastUpdateDt", "createdDt"],
    buttonsKey: "buttons",
    logFileNameKey: "logFileName",
    logLinkKey: "instanceId",
    dateKeys: ["lastUpdateDt", "createdDt"],
    statusKey: "status",

    canBeKilledStatuses: [process.runningStatus],
    failedStatuses: [process.failedStatus],

    defaultSortKey: "lastUpdateDt",
    defaultSortDir: sort.DESC,
};

export const log = {
    fetchIncrement: 2048,
    fetchDelay: 5000,
    defaultFetchRange: { low: undefined, high: 2048 }
};