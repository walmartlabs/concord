import history, * as fromHistory from "./history";
import log, * as fromLog from "./log";
import * as fromSession from "./session";

export default {history, process, log};

export const getHistoryRows = (state) => fromHistory.getRows(state.history);
export const getIsHistoryLoading = (state) => fromHistory.getIsLoading(state.history);
export const getHistoryLoadingError = (state) => fromHistory.getError(state.history);
export const getHistoryLastQuery = (state) => fromHistory.getLastQuery(state.history);

export const getLogData = (state) => fromLog.getData(state.log);
export const getIsLogLoading = (state) => fromLog.getIsLoading(state.log);
export const getLogLoadingError = (state) => fromLog.getError(state.log);
export const getLoadedLogRange = (state) => fromLog.getRange(state.log);
export const getLoadedLogStatus = (state) => fromLog.getStatus(state.log);

export const getIsLoggedIn = (state) => fromSession.getIsLoggedIn(state.session);
