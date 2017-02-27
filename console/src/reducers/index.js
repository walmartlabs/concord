import history from "../containers/VisibleHistoryTable/reducers";
import projectList from "../containers/VisibleProjectTable/reducers";
import project from "../containers/VisibleProjectForm/reducers";
import log from "../containers/VisibleLogViewer/reducers";
import templateList from "../containers/VisibleTemplateList/reducers";
import * as fromSession from "./session";

export default {history, projectList, project, templateList, log};

export const getHistoryState = (state) => state.history;
export const getProjectListState = (state) => state.projectList;
export const getLogState = (state) => state.log;
export const getProjectState = (state) => state.project;
export const getTemplateListState = (state) => state.templateList;

export const getIsLoggedIn = (state) => fromSession.getIsLoggedIn(state.session);
