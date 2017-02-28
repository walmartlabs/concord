// @flow
import {call, put, select} from "redux-saga/effects";
import {makeListFetcher} from "../../sagas/common";
import * as projectApi from "../../api/project";
import {getProjectListState as getState} from "../../reducers";
import {getLastQuery} from "./reducers";
import {actionTypes} from "./actions";

export const fetchProjectList = makeListFetcher("fetchProjectList", projectApi.fetchProjectList,
    actionTypes.FETCH_PROJECT_LIST_RESULT);

export function* deleteProject(action: any): any {
    try {
        yield call(projectApi.deleteProject, action.name);
        yield put({
            type: actionTypes.DELETE_PROJECT_RESULT,
            name: action.name
        });

        const query = yield select((state) => getLastQuery(getState(state)));
        yield put({
            type: actionTypes.FETCH_PROJECT_LIST_REQUEST,
            ...query
        });
    } catch (e) {
        console.error("deleteProject -> error", e);
        yield put({
            type: actionTypes.DELETE_PROJECT_RESULT,
            name: action.name,
            error: true,
            message: e.message || "Error while removing a project"
        });
    }
}
