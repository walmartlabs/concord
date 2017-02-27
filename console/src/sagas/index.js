// @flow
import {takeLatest, fork} from "redux-saga/effects";
import {fetchProjectList, deleteProject} from "../containers/VisibleProjectTable/sagas";
import {fetchHistoryData, killProc} from "../containers/VisibleHistoryTable/sagas";
import {fetchProjectData, updateProjectData, createProject} from "../containers/VisibleProjectForm/sagas";
import {fetchLogData} from "../containers/VisibleLogViewer/sagas";
import {fetchTemplateList} from "../containers/VisibleTemplateList/sagas";
import {actionTypes as projectListActionTypes} from "../containers/VisibleProjectTable/actions";
import {actionTypes as historyActionTypes} from "../containers/VisibleHistoryTable/actions";
import {actionTypes as logActionTypes} from "../containers/VisibleLogViewer/actions";
import {actionTypes as projectActionTypes} from "../containers/VisibleProjectForm/actions";
import {actionTypes as templateListActionTypes} from "../containers/VisibleTemplateList/actions";

export default function* saga(): Generator<*, *, *> {
    // history
    yield fork(takeLatest, historyActionTypes.FETCH_HISTORY_DATA_REQUEST, fetchHistoryData);
    yield fork(takeLatest, historyActionTypes.KILL_PROC_REQUEST, killProc);

    // project list
    yield fork(takeLatest, projectListActionTypes.FETCH_PROJECT_LIST_REQUEST, fetchProjectList);
    yield fork(takeLatest, projectListActionTypes.DELETE_PROJECT_REQUEST, deleteProject);

    // project
    yield fork(takeLatest, projectActionTypes.FETCH_PROJECT_REQUEST, fetchProjectData);
    yield fork(takeLatest, projectActionTypes.UPDATE_PROJECT_REQUEST, updateProjectData);
    yield fork(takeLatest, projectActionTypes.CREATE_PROJECT_REQUEST, createProject);

    // template list
    yield fork(takeLatest, templateListActionTypes.FETCH_TEMPLATE_LIST_REQUEST, fetchTemplateList);

    // log
    yield fork(takeLatest, logActionTypes.FETCH_LOG_DATA_REQUEST, fetchLogData);
}
