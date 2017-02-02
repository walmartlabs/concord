import actionTypes from "./actionTypes";

// VisibleHistoryTable

export const fetchHistoryData = (sortBy, sortDir) => ({
    type: actionTypes.history.FETCH_HISTORY_DATA_REQUEST,
    sortBy,
    sortDir
});

export const killProc = (id) => ({
    type: actionTypes.history.KILL_PROC_REQUEST,
    id
});

// VisibleLogViewer

export const fetchLogData = (instanceId, fetchRange, fresh) => ({
    type: actionTypes.log.FETCH_LOG_DATA_REQUEST,
    instanceId,
    fetchRange,
    fresh
});

// VisibleLoginForm

export const doLogin = (values) => {
    // TODO
};
