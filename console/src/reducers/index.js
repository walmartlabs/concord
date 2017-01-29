import history, * as fromHistory from "./history";
import log, * as fromLog from "./log";
import session, * as fromSession from "./session";

export default {history, log, session};

export const getHistoryRows = (state) => fromHistory.getRows(state.history);
export const getIsHistoryLoading = (state) => fromHistory.getIsLoading(state.history);
export const getHistoryLoadingError = (state) => fromHistory.getError(state.history);

export const getLogData = (state) => fromLog.getData(state.log);
export const getIsLogLoading = (state) => fromLog.getIsLoading(state.log);
export const getLogLoadingError = (state) => fromLog.getError(state.log);

export const getIsLoggedIn = (state) => fromSession.getIsLoggedIn(state.session);
