// @flow
import type {ConcordId, SortDirection} from "../../types";

export const actionTypes = {
    FETCH_HISTORY_DATA_REQUEST: "FETCH_HISTORY_DATA_REQUEST",
    FETCH_HISTORY_DATA_RESULT: "FETCH_HISTORY_DATA_RESULT",

    KILL_PROC_REQUEST: "KILL_PROC_REQUEST",
    KILL_PROC_RESULT: "KILL_PROC_RESULT"
};

export const fetchHistoryData = (sortBy: string, sortDir: SortDirection) => ({
    type: actionTypes.FETCH_HISTORY_DATA_REQUEST,
    sortBy,
    sortDir
});

export const killProc = (id: ConcordId) => ({
    type: actionTypes.KILL_PROC_REQUEST,
    id
});
