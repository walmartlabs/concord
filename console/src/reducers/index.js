import history, * as fromHistory from "./history";
import projectList, * as fromProjectList from "./projectList";
import log, * as fromLog from "./log";
import * as fromSession from "./session";

export default {history, projectList, process, log};

export const getHistoryRows = (state) => fromHistory.getRows(state.history);
export const getIsHistoryLoading = (state) => fromHistory.getIsLoading(state.history);
export const getHistoryLoadingError = (state) => fromHistory.getError(state.history);
export const getHistoryLastQuery = (state) => fromHistory.getLastQuery(state.history);
export const getIsHistoryRecordInFlight = (state, id) => fromHistory.isInFlight(state.history, id);

export const getProjectListRows = (state) => fromProjectList.getRows(state.projectList);
export const getIsProjectListLoading = (state) => fromProjectList.getIsLoading(state.projectList);
export const getProjectListLoadingError = (state) => fromProjectList.getError(state.projectList);
export const getProjectListLastQuery = (state) => fromProjectList.getLastQuery(state.projectList);
export const getIsProjectInFlight = (state, id) => fromProjectList.isInFlight(state.projectList, id);

export const getLogData = (state) => fromLog.getData(state.log);
export const getIsLogLoading = (state) => fromLog.getIsLoading(state.log);
export const getLogLoadingError = (state) => fromLog.getError(state.log);
export const getLoadedLogRange = (state) => fromLog.getRange(state.log);
export const getLoadedLogStatus = (state) => fromLog.getStatus(state.log);

export const getIsLoggedIn = (state) => fromSession.getIsLoggedIn(state.session);
