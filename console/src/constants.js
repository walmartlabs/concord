export const sort = {
    ASC: "ASC",
    DESC: "DESC"
};

export const reverseSort = (dir) => dir === sort.ASC ? sort.DESC : sort.ASC;

export const history = {
    columns: [
        {key: "instanceId", label: "Instance ID"},
        {key: "status", label: "Status"},
        {key: "initiator", label: "Initiator"},
        {key: "lastUpdateDt", label: "Updated"},
        {key: "createdDt", label: "Created"}
    ],

    defaultSortKey: "lastUpdateDt",
    defaultSortDir: sort.DESC,

    logFileNameKey: "logFileName",
    logLinkKey: "instanceId",

    dateKeys: ["lastUpdateDt", "createdDt"],

    statusKey: "status",

    failedStatus: "FAILED"
};

export const projects = {
    columns: [
        {key: "projectId", label: "Project ID"},
        {key: "name", label: "Name"}
    ]
};
