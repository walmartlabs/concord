// @flow
import type {ConcordId} from "../../types";

export const actionTypes = {
    FETCH_PROCESS_STATUS_REQUEST: "FETCH_PROCESS_STATUS_REQUEST",
    FETCH_PROCESS_STATUS_RESULT: "FETCH_PROCESS_STATUS_RESULT",

    KILL_PROCESS_REQUEST: "KILL_PROCESS_REQUEST",
    KILL_PROCESS_RESULT: "KILL_PROCESS_RESULT"
};

export const fetchData = (instanceId: ConcordId) => ({
    type: actionTypes.FETCH_PROCESS_STATUS_REQUEST,
    instanceId,
    includeForms: true
});

export const kill = (instanceId: ConcordId) => ({
    type: actionTypes.KILL_PROCESS_REQUEST,
    instanceId
});