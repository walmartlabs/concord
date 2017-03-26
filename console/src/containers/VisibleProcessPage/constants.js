// @flow
import * as global from "../../constants";

export const activeStatuses = [global.process.status.runningStatus, global.process.status.startingStatus, global.process.status.resumingStatus];
export const canBeKilledStatuses = [global.process.status.runningStatus, global.process.status.suspendedStatus];
export const enableFormsStatuses = [global.process.status.suspendedStatus];
