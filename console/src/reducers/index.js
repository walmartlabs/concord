import history from "../containers/VisibleHistoryTable/reducers";
import secretList from "../containers/VisibleSecretTable/reducers";
import projectList from "../containers/VisibleProjectTable/reducers";
import project from "../containers/VisibleProjectForm/reducers";
import log from "../containers/VisibleLogViewer/reducers";
import templateList from "../containers/VisibleTemplateList/reducers";
import processForm from "../containers/VisibleProcessForm/reducers";
import process from "../containers/VisibleProcessPage/reducers";
import processWizard from "../containers/VisibleProcessWizard/reducers";
import portal from "../containers/VisiblePortalPage/reducers";
import * as fromSession from "./session";

export default {history, secretList, projectList, project, templateList, log, processForm, process, processWizard, portal};

export const getHistoryState = (state) => state.history;
export const getSecretListState = (state) => state.secretList;
export const getProjectListState = (state) => state.projectList;
export const getLogState = (state) => state.log;
export const getProjectState = (state) => state.project;
export const getTemplateListState = (state) => state.templateList;
export const getProcessFormState = (state) => state.processForm;
export const getProcessState = (state) => state.process;
export const getProcessWizardState = (state) => state.processWizard;
export const getPortalState = (state) => state.portal;

export const getIsLoggedIn = (state) => fromSession.getIsLoggedIn(state.session);
