/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import { push as pushHistory } from 'connected-react-router';
import { Action, combineReducers, Reducer } from 'redux';
import { all, call, put, takeLatest } from 'redux-saga/effects';

import { ConcordKey } from '../../../api/common';
import { ResourceAccessEntry } from '../../../api/org';
import {
    createOrUpdate as apiCreateOrUpdate,
    deleteProject as apiDeleteProject,
    get as apiGet,
    getProjectAccess,
    list as apiList,
    NewProjectEntry,
    updateProjectAccess,
    UpdateProjectEntry
} from '../../../api/org/project';
import {
    createOrUpdate as apiRepoCreateOrUpdate,
    deleteRepository as apiRepoDelete,
    EditRepositoryEntry,
    refreshRepository as apiRepoRefresh,
    validateRepository as apiRepoValidation
} from '../../../api/org/project/repository';
import {
    genericResult,
    handleErrors,
    makeErrorReducer,
    makeLoadingReducer,
    makeResponseReducer
} from '../common';
import {
    AddRepositoryRequest,
    CreateProjectRequest,
    CreateRepositoryState,
    DeleteProjectRequest,
    DeleteProjectState,
    DeleteRepositoryRequest,
    DeleteRepositoryState,
    GetProjectRequest,
    ListProjectsRequest,
    PaginatedProjects,
    Pagination,
    ProjectDataResponse,
    ProjectTeamAccessRequest,
    ProjectTeamAccessState,
    RefreshRepositoryRequest,
    RefreshRepositoryState,
    State,
    UpdateProjectRequest,
    updateProjectState,
    UpdateProjectTeamAccessRequest,
    UpdateProjectTeamAccessState,
    UpdateRepositoryRequest,
    UpdateRepositoryState,
    ValidateRepositoryRequest,
    ValidateRepositoryState
} from './types';

// https://github.com/facebook/create-react-app/issues/6054
export * from './types';

const NAMESPACE = 'projects';

const actionTypes = {
    GET_PROJECT_REQUEST: `${NAMESPACE}/get/request`,
    LIST_PROJECTS_REQUEST: `${NAMESPACE}/list/request`,
    PROJECT_DATA_RESPONSE: `${NAMESPACE}/data/response`,

    CREATE_PROJECT_REQUEST: `${NAMESPACE}/create/request`,

    DELETE_PROJECT_REQUEST: `${NAMESPACE}/delete/request`,
    DELETE_PROJECT_RESPONSE: `${NAMESPACE}/delete/response`,

    ADD_REPOSITORY_REQUEST: `${NAMESPACE}/repo/add/request`,
    ADD_REPOSITORY_RESPONSE: `${NAMESPACE}/repo/add/response`,

    UPDATE_REPOSITORY_REQUEST: `${NAMESPACE}/repo/update/request`,
    UPDATE_REPOSITORY_RESPONSE: `${NAMESPACE}/repo/update/response`,

    DELETE_REPOSITORY_REQUEST: `${NAMESPACE}/repo/delete/request`,
    DELETE_REPOSITORY_RESPONSE: `${NAMESPACE}/repo/delete/response`,

    REFRESH_REPOSITORY_REQUEST: `${NAMESPACE}/repo/refresh/request`,
    REFRESH_REPOSITORY_RESPONSE: `${NAMESPACE}/repo/refresh/response`,

    VALIDATE_REPOSITORY_REQUEST: `${NAMESPACE}/repo/validate/request`,
    VALIDATE_REPOSITORY_RESPONSE: `${NAMESPACE}/repo/validate/response`,

    RESET_REPOSITORY: `${NAMESPACE}/repo/reset`,

    UPDATE_PROJECT_REQUEST: `${NAMESPACE}/updateproject/request`,
    UPDATE_PROJECT_RESPONSE: `${NAMESPACE}/updateproject/response`,

    PROJECT_TEAM_ACCESS_REQUEST: `${NAMESPACE}/project/teamaccess/request`,
    PROJECT_TEAM_ACCESS_RESPONSE: `${NAMESPACE}/project/teamaccess/response`,

    UPDATE_PROJECT_TEAM_ACCESS_REQUEST: `${NAMESPACE}/project/teamaccess/update/request`,
    UPDATE_PROJECT_TEAM_ACCESS_RESPONSE: `${NAMESPACE}/project/teamaccess/update/response`,
    RESET_PROJECT_TEAM_ACCESS: `${NAMESPACE}/project/teamaccess/reset`
};

export const actions = {
    getProject: (orgName: ConcordKey, projectName: ConcordKey): GetProjectRequest => ({
        type: actionTypes.GET_PROJECT_REQUEST,
        orgName,
        projectName
    }),

    listProjects: (
        orgName: ConcordKey,
        pagination: Pagination,
        filter?: string
    ): ListProjectsRequest => ({
        type: actionTypes.LIST_PROJECTS_REQUEST,
        orgName,
        pagination,
        filter
    }),

    createProject: (orgName: ConcordKey, entry: NewProjectEntry): CreateProjectRequest => ({
        type: actionTypes.CREATE_PROJECT_REQUEST,
        orgName,
        entry
    }),

    updateProject: (orgName: ConcordKey, entry: UpdateProjectEntry): UpdateProjectRequest => ({
        type: actionTypes.UPDATE_PROJECT_REQUEST,
        orgName,
        entry
    }),

    deleteProject: (orgName: ConcordKey, projectName: ConcordKey): DeleteProjectRequest => ({
        type: actionTypes.DELETE_PROJECT_REQUEST,
        orgName,
        projectName
    }),

    addRepository: (
        orgName: ConcordKey,
        projectName: ConcordKey,
        entry: EditRepositoryEntry
    ): AddRepositoryRequest => ({
        type: actionTypes.ADD_REPOSITORY_REQUEST,
        orgName,
        projectName,
        entry
    }),

    updateRepository: (
        orgName: ConcordKey,
        projectName: ConcordKey,
        entry: EditRepositoryEntry
    ): UpdateRepositoryRequest => ({
        type: actionTypes.UPDATE_REPOSITORY_REQUEST,
        orgName,
        projectName,
        entry
    }),

    deleteRepository: (
        orgName: ConcordKey,
        projectName: ConcordKey,
        repoName: ConcordKey
    ): DeleteRepositoryRequest => ({
        type: actionTypes.DELETE_REPOSITORY_REQUEST,
        orgName,
        projectName,
        repoName
    }),

    refreshRepository: (
        orgName: ConcordKey,
        projectName: ConcordKey,
        repoName: ConcordKey
    ): RefreshRepositoryRequest => ({
        type: actionTypes.REFRESH_REPOSITORY_REQUEST,
        orgName,
        projectName,
        repoName
    }),

    validateRepository: (
        orgName: ConcordKey,
        projectName: ConcordKey,
        repoName: ConcordKey
    ): ValidateRepositoryRequest => ({
        type: actionTypes.VALIDATE_REPOSITORY_REQUEST,
        orgName,
        projectName,
        repoName
    }),

    getTeamAccess: (orgName: ConcordKey, projectName: ConcordKey): ProjectTeamAccessRequest => ({
        type: actionTypes.PROJECT_TEAM_ACCESS_REQUEST,
        orgName,
        projectName
    }),

    updateTeamAccess: (
        orgName: ConcordKey,
        projectName: ConcordKey,
        teams: ResourceAccessEntry[]
    ): UpdateProjectTeamAccessRequest => ({
        type: actionTypes.UPDATE_PROJECT_TEAM_ACCESS_REQUEST,
        orgName,
        projectName,
        teams
    }),

    resetRepository: (): Action => ({
        type: actionTypes.RESET_REPOSITORY
    }),
    reset: () => ({
        type: actionTypes.RESET_PROJECT_TEAM_ACCESS
    })
};

const projectById: Reducer<PaginatedProjects> = (state = {}, action: ProjectDataResponse) => {
    switch (action.type) {
        case actionTypes.PROJECT_DATA_RESPONSE:
            const a = action as ProjectDataResponse;

            if (a.error || !a.items) {
                return state;
            }

            const result = {};

            a.items.forEach((o) => {
                result[o.id] = o;
            });

            return { items: result, next: a.next };
        default:
            return state;
    }
};

const loading = makeLoadingReducer(
    [
        actionTypes.GET_PROJECT_REQUEST,
        actionTypes.LIST_PROJECTS_REQUEST,
        actionTypes.CREATE_PROJECT_REQUEST
    ],
    [actionTypes.PROJECT_DATA_RESPONSE]
);

const errorMsg = makeErrorReducer(
    [
        actionTypes.GET_PROJECT_REQUEST,
        actionTypes.LIST_PROJECTS_REQUEST,
        actionTypes.CREATE_PROJECT_REQUEST
    ],
    [actionTypes.PROJECT_DATA_RESPONSE]
);

const updateProjectReducers = combineReducers<updateProjectState>({
    running: makeLoadingReducer(
        [actionTypes.UPDATE_PROJECT_REQUEST],
        [actionTypes.UPDATE_PROJECT_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.UPDATE_PROJECT_REQUEST],
        [actionTypes.UPDATE_PROJECT_RESPONSE]
    ),
    response: makeResponseReducer(
        actionTypes.UPDATE_PROJECT_REQUEST,
        actionTypes.UPDATE_PROJECT_RESPONSE
    )
});

const deleteProjectReducers = combineReducers<DeleteProjectState>({
    running: makeLoadingReducer(
        [actionTypes.DELETE_PROJECT_REQUEST],
        [actionTypes.DELETE_PROJECT_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.DELETE_PROJECT_REQUEST],
        [actionTypes.DELETE_PROJECT_RESPONSE]
    ),
    response: makeResponseReducer(
        actionTypes.DELETE_PROJECT_RESPONSE,
        actionTypes.DELETE_PROJECT_REQUEST
    )
});

const createRepositoryReducers = combineReducers<CreateRepositoryState>({
    running: makeLoadingReducer(
        [actionTypes.ADD_REPOSITORY_REQUEST],
        [actionTypes.ADD_REPOSITORY_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.ADD_REPOSITORY_REQUEST],
        [actionTypes.ADD_REPOSITORY_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.ADD_REPOSITORY_RESPONSE, actionTypes.RESET_REPOSITORY)
});

const updateRepositoryReducers = combineReducers<UpdateRepositoryState>({
    running: makeLoadingReducer(
        [actionTypes.UPDATE_REPOSITORY_REQUEST],
        [actionTypes.UPDATE_REPOSITORY_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.UPDATE_REPOSITORY_REQUEST],
        [actionTypes.UPDATE_REPOSITORY_RESPONSE]
    ),
    response: makeResponseReducer(
        actionTypes.UPDATE_REPOSITORY_RESPONSE,
        actionTypes.RESET_REPOSITORY
    )
});

const deleteRepositoryReducers = combineReducers<DeleteRepositoryState>({
    running: makeLoadingReducer(
        [actionTypes.DELETE_REPOSITORY_REQUEST],
        [actionTypes.DELETE_REPOSITORY_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.DELETE_REPOSITORY_REQUEST],
        [actionTypes.DELETE_REPOSITORY_RESPONSE]
    ),
    response: makeResponseReducer(
        actionTypes.DELETE_REPOSITORY_RESPONSE,
        actionTypes.RESET_REPOSITORY
    )
});

const refreshRepositoryReducers = combineReducers<RefreshRepositoryState>({
    running: makeLoadingReducer(
        [actionTypes.REFRESH_REPOSITORY_REQUEST],
        [actionTypes.RESET_REPOSITORY, actionTypes.REFRESH_REPOSITORY_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.REFRESH_REPOSITORY_REQUEST],
        [actionTypes.RESET_REPOSITORY, actionTypes.REFRESH_REPOSITORY_RESPONSE]
    ),
    response: makeResponseReducer(
        actionTypes.REFRESH_REPOSITORY_RESPONSE,
        actionTypes.RESET_REPOSITORY
    )
});

const validateRepositoryReducers = combineReducers<ValidateRepositoryState>({
    running: makeLoadingReducer(
        [actionTypes.VALIDATE_REPOSITORY_REQUEST],
        [actionTypes.VALIDATE_REPOSITORY_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.VALIDATE_REPOSITORY_REQUEST],
        [actionTypes.VALIDATE_REPOSITORY_RESPONSE, actionTypes.RESET_REPOSITORY]
    ),
    response: makeResponseReducer(
        actionTypes.VALIDATE_REPOSITORY_RESPONSE,
        actionTypes.RESET_REPOSITORY
    )
});

const getTeamAccessReducers = combineReducers<ProjectTeamAccessState>({
    running: makeLoadingReducer(
        [actionTypes.PROJECT_TEAM_ACCESS_REQUEST],
        [actionTypes.PROJECT_TEAM_ACCESS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.PROJECT_TEAM_ACCESS_REQUEST],
        [actionTypes.PROJECT_TEAM_ACCESS_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.PROJECT_TEAM_ACCESS_RESPONSE)
});

const updateTeamAccessReducers = combineReducers<UpdateProjectTeamAccessState>({
    running: makeLoadingReducer(
        [actionTypes.UPDATE_PROJECT_TEAM_ACCESS_REQUEST],
        [actionTypes.UPDATE_PROJECT_TEAM_ACCESS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.UPDATE_PROJECT_TEAM_ACCESS_REQUEST],
        [actionTypes.UPDATE_PROJECT_TEAM_ACCESS_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.UPDATE_PROJECT_TEAM_ACCESS_RESPONSE)
});

export const reducers = combineReducers<State>({
    projectById, // TODO use makeEntityByIdReducer

    loading,
    error: errorMsg,

    deleteProject: deleteProjectReducers,
    updateProject: updateProjectReducers,
    projectTeamAccess: getTeamAccessReducers,
    updateProjectTeamAccess: updateTeamAccessReducers,

    createRepository: createRepositoryReducers,
    updateRepository: updateRepositoryReducers,
    deleteRepository: deleteRepositoryReducers,
    refreshRepository: refreshRepositoryReducers,
    validateRepository: validateRepositoryReducers
});

export const selectors = {
    projectNames: (state: State, orgName: ConcordKey): ConcordKey[] => {
        if (!state.projectById.items) {
            return [];
        }

        const projectsById = state.projectById.items;

        return Object.keys(projectsById)
            .map((id) => projectsById[id])
            .filter((p) => p.orgName === orgName)
            .map((p) => p.name)
            .sort();
    },

    projectByName: (state: State, orgName: ConcordKey, projectName: ConcordKey) => {
        if (!state.projectById.items) {
            return;
        }

        for (const id of Object.keys(state.projectById.items)) {
            const p = state.projectById.items[id];
            if (p.orgName === orgName && p.name === projectName) {
                return p;
            }
        }

        return;
    },

    projectAccesTeams: (state: State, orgName: ConcordKey, projectName: ConcordKey) => {
        const p = state.projectTeamAccess.response ? state.projectTeamAccess.response.items : [];
        return p ? p : [];
    }
};

function* onGet({ orgName, projectName }: GetProjectRequest) {
    try {
        const response = yield call(apiGet, orgName, projectName);
        yield put({
            type: actionTypes.PROJECT_DATA_RESPONSE,
            items: [response] // normalizing the data
        });
    } catch (e) {
        yield handleErrors(actionTypes.PROJECT_DATA_RESPONSE, e);
    }
}

function* onList({ orgName, pagination, filter }: ListProjectsRequest) {
    try {
        const response = yield call(apiList, orgName, pagination.offset, pagination.limit, filter);
        yield put({
            type: actionTypes.PROJECT_DATA_RESPONSE,
            items: response.items,
            next: response.next,
            prev: response.prev
        });
    } catch (e) {
        yield handleErrors(actionTypes.PROJECT_DATA_RESPONSE, e);
    }
}

function* onCreate({ orgName, entry }: CreateProjectRequest) {
    try {
        yield call(apiCreateOrUpdate, orgName, entry);
        yield put({
            type: actionTypes.PROJECT_DATA_RESPONSE
        });

        yield put(pushHistory(`/org/${orgName}/project/${entry.name}`));
    } catch (e) {
        yield handleErrors(actionTypes.PROJECT_DATA_RESPONSE, e);
    }
}

function* onUpdateProject({ orgName, entry }: UpdateProjectRequest) {
    try {
        yield call(apiCreateOrUpdate, orgName, entry);
        yield put({
            type: actionTypes.UPDATE_PROJECT_RESPONSE
        });

        yield put(actions.getProject(orgName, entry.name!));
    } catch (e) {
        yield handleErrors(actionTypes.UPDATE_PROJECT_RESPONSE, e);
    }
}

function* onDelete({ orgName, projectName }: DeleteProjectRequest) {
    try {
        yield call(apiDeleteProject, orgName, projectName);
        yield put({
            type: actionTypes.DELETE_PROJECT_RESPONSE
        });

        yield put(pushHistory(`/org/${orgName}/project`));
    } catch (e) {
        yield handleErrors(actionTypes.DELETE_PROJECT_RESPONSE, e);
    }
}

function* onAddRepository({ orgName, projectName, entry }: AddRepositoryRequest) {
    try {
        const response = yield call(apiRepoCreateOrUpdate, orgName, projectName, entry);
        yield put(genericResult(actionTypes.ADD_REPOSITORY_RESPONSE, response));

        yield put(pushHistory(`/org/${orgName}/project/${projectName}/repository`));
    } catch (e) {
        yield handleErrors(actionTypes.ADD_REPOSITORY_RESPONSE, e);
    }
}

function* onUpdateRepository({ orgName, projectName, entry }: UpdateRepositoryRequest) {
    try {
        const response = yield call(apiRepoCreateOrUpdate, orgName, projectName, entry);
        yield put(genericResult(actionTypes.UPDATE_REPOSITORY_RESPONSE, response));

        yield put(pushHistory(`/org/${orgName}/project/${projectName}/repository`));
    } catch (e) {
        yield handleErrors(actionTypes.UPDATE_REPOSITORY_RESPONSE, e);
    }
}

function* onDeleteRepository({ orgName, projectName, repoName }: DeleteRepositoryRequest) {
    try {
        const response = yield call(apiRepoDelete, orgName, projectName, repoName);
        yield put(genericResult(actionTypes.DELETE_REPOSITORY_RESPONSE, response));
    } catch (e) {
        yield handleErrors(actionTypes.DELETE_REPOSITORY_RESPONSE, e);
    }
}

function* onRefreshRepository({ orgName, projectName, repoName }: RefreshRepositoryRequest) {
    try {
        const response = yield call(apiRepoRefresh, orgName, projectName, repoName, true);
        yield put(genericResult(actionTypes.REFRESH_REPOSITORY_RESPONSE, response));
    } catch (e) {
        yield handleErrors(actionTypes.REFRESH_REPOSITORY_RESPONSE, e);
    }
}

function* onValidateRepository({ orgName, projectName, repoName }: ValidateRepositoryRequest) {
    try {
        const response = yield call(apiRepoValidation, orgName, projectName, repoName);
        yield put(genericResult(actionTypes.VALIDATE_REPOSITORY_RESPONSE, response));
    } catch (e) {
        yield handleErrors(actionTypes.VALIDATE_REPOSITORY_RESPONSE, e);
    }
}

function* onGetTeamAccess({ orgName, projectName }: ProjectTeamAccessRequest) {
    try {
        const response = yield call(getProjectAccess, orgName, projectName);
        yield put({
            type: actionTypes.PROJECT_TEAM_ACCESS_RESPONSE,
            items: response
        });
    } catch (e) {
        yield handleErrors(actionTypes.PROJECT_TEAM_ACCESS_RESPONSE, e);
    }
}

function* onUpdateTeamAccess({ orgName, projectName, teams }: UpdateProjectTeamAccessRequest) {
    try {
        const response = yield call(updateProjectAccess, orgName, projectName, teams);
        yield put(genericResult(actionTypes.UPDATE_PROJECT_TEAM_ACCESS_RESPONSE, response));
        yield put(actions.getTeamAccess(orgName, projectName));
    } catch (e) {
        yield handleErrors(actionTypes.UPDATE_PROJECT_TEAM_ACCESS_RESPONSE, e);
    }
}

export const sagas = function*() {
    yield all([
        takeLatest(actionTypes.GET_PROJECT_REQUEST, onGet),
        takeLatest(actionTypes.LIST_PROJECTS_REQUEST, onList),
        takeLatest(actionTypes.CREATE_PROJECT_REQUEST, onCreate),
        takeLatest(actionTypes.DELETE_PROJECT_REQUEST, onDelete),
        takeLatest(actionTypes.UPDATE_PROJECT_REQUEST, onUpdateProject),
        takeLatest(actionTypes.ADD_REPOSITORY_REQUEST, onAddRepository),
        takeLatest(actionTypes.UPDATE_REPOSITORY_REQUEST, onUpdateRepository),
        takeLatest(actionTypes.DELETE_REPOSITORY_REQUEST, onDeleteRepository),
        takeLatest(actionTypes.REFRESH_REPOSITORY_REQUEST, onRefreshRepository),
        takeLatest(actionTypes.VALIDATE_REPOSITORY_REQUEST, onValidateRepository),
        takeLatest(actionTypes.PROJECT_TEAM_ACCESS_REQUEST, onGetTeamAccess),
        takeLatest(actionTypes.UPDATE_PROJECT_TEAM_ACCESS_REQUEST, onUpdateTeamAccess)
    ]);
};
