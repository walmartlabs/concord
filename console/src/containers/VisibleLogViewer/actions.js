// @flow
import type {ConcordId, FetchRange} from "../../types";

export const actionTypes = {
    FETCH_LOG_DATA_REQUEST: "FETCH_LOG_DATA_REQUEST",
    FETCH_LOG_DATA_RESULT: "FETCH_LOG_DATA_RESULT"
};

export const fetchLogData = (instanceId: ConcordId, fetchRange: FetchRange, reset: boolean) => ({
    type: actionTypes.FETCH_LOG_DATA_REQUEST,
    instanceId,
    fetchRange,
    reset
});
