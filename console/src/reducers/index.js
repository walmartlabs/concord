import history, * as fromHistory from "./history";
import projectList, * as fromProjectList from "./projectList";
import project, * as fromProject from "./project";
import templateList, * as fromTemplateList from "./templateList";
import log, * as fromLog from "./log";
import * as fromSession from "./session";

export default {history, projectList, project, templateList, process, log};

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

export const getProjectData = (state) => fromProject.getData(state.project);
export const getIsProjectDataLoading = (state) => fromProject.getIsLoading(state.project);
export const getProjectLoadingError = (state) => fromProject.getError(state.project);

export const getTemplateListRows = (state) => fromTemplateList.getRows(state.templateList);
export const getIsTemplateListLoading = (state) => fromTemplateList.getIsLoading(state.templateList);
export const getTemplateListLoadingError = (state) => fromTemplateList.getError(state.templateList);

export const getLogData = (state) => fromLog.getData(state.log);
export const getIsLogLoading = (state) => fromLog.getIsLoading(state.log);
export const getLogLoadingError = (state) => fromLog.getError(state.log);
export const getLoadedLogRange = (state) => fromLog.getRange(state.log);
export const getLoadedLogStatus = (state) => fromLog.getStatus(state.log);

export const getIsLoggedIn = (state) => fromSession.getIsLoggedIn(state.session);
