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

export const fetchLogData = (fileName) => ({
    type: actionTypes.log.FETCH_LOG_DATA_REQUEST,
    fileName
});

// VisibleLoginForm

export const doLogin = (values) => {
    // TODO
};
