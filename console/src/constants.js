export const sort = {
    ASC: "ASC",
    DESC: "DESC"
};

export const reverseSort = (dir) => dir === sort.ASC ? sort.DESC : sort.ASC;

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

    canBeKilledStatuses: ["RUNNING"],
    failedStatuses: ["FAILED"],

    defaultSortKey: "lastUpdateDt",
    defaultSortDir: sort.DESC,
};

export const projects = {
    columns: [
        {key: "projectId", label: "Project ID"},
        {key: "name", label: "Name"}
    ]
};
