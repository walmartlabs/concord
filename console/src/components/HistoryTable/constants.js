// @flow
import * as global from "../../constants";
import {sort} from "../../constants";

export const columns = [
    {key: "instanceId", label: "Instance ID", collapsing: true},
    {key: "status", label: "Status"},
    {key: "initiator", label: "Initiator"},
    {key: "lastUpdateDt", label: "Updated"},
    {key: "createdDt", label: "Created"},
    {key: "actions", label: "Actions", collapsing: true}
];

export const idKey = "instanceId";
export const sortableKeys = ["status", "initiator", "lastUpdateDt", "createdDt"];
export const actionsKey = "actions";
export const logFileNameKey = "logFileName";
export const logLinkKey = "instanceId";
export const dateKeys = ["lastUpdateDt", "createdDt"];
export const statusKey = "status";

export const canBeKilledStatuses = [global.process.status.runningStatus, global.process.status.suspendedStatus];
export const failedStatuses = [global.process.status.failedStatus];

export const defaultSortKey = "lastUpdateDt";
export const defaultSortDir = sort.DESC;
