// @flow
import type {SortDirection} from "../types";
import {sort as sortConstants} from "../types";

export const sort = sortConstants;

export const reverseSort = (dir: SortDirection): SortDirection => dir === sort.ASC ? sort.DESC : sort.ASC;

export const process = {
    runningStatus: "RUNNING",
    failedStatus: "FAILED",
    finishedStatus: "FINISHED",
    startingStatus: "STARTING"
};
