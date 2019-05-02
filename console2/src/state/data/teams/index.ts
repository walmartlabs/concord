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
import { combineReducers } from 'redux';
import { all, call, put, takeLatest } from 'redux-saga/effects';

import { ConcordId, ConcordKey } from '../../../api/common';
import {
    addUsers as apiAddUsers,
    createOrUpdate as apiCreateOrUpdate,
    deleteTeam as apiDeleteTeam,
    get as apiGet,
    list as apiList,
    listUsers as apiListUsers,
    listLdapGroups as apiListLdapGroups,
    addLdapGroups as apiReplaceLdapGroups,
    NewTeamEntry,
    NewTeamLdapGroupEntry,
    NewTeamUserEntry,
    rename as apiRename
} from '../../../api/org/team';
import {
    handleErrors,
    makeEntityByIdReducer,
    makeErrorReducer,
    makeLoadingReducer,
    makeResponseReducer
} from '../common';
import {
    CreateOrUpdateTeamState,
    CreateTeamRequest,
    DeleteTeamRequest,
    DeleteTeamState,
    GetTeamRequest,
    GetTeamState,
    ListTeamLdapGroupsRequest,
    ListTeamLdapGroupsState,
    ListTeamsRequest,
    ListTeamsState,
    ListTeamUsersRequest,
    ListTeamUsersState,
    RenameTeamRequest,
    RenameTeamState,
    ReplaceTeamLdapGroupsRequest,
    ReplaceTeamLdapGroupsState,
    ReplaceTeamUsersRequest,
    ReplaceTeamUsersState,
    State
} from './types';

// https://github.com/facebook/create-react-app/issues/6054
export * from './types';

const NAMESPACE = 'teams';

const actionTypes = {
    GET_TEAM_REQUEST: `${NAMESPACE}/get/request`,
    LIST_TEAMS_REQUEST: `${NAMESPACE}/list/request`,
    TEAM_DATA_RESPONSE: `${NAMESPACE}/data/response`,

    CREATE_TEAM_REQUEST: `${NAMESPACE}/create/request`,
    CREATE_TEAM_RESPONSE: `${NAMESPACE}/create/response`,

    RENAME_TEAM_REQUEST: `${NAMESPACE}/rename/request`,
    RENAME_TEAM_RESPONSE: `${NAMESPACE}/rename/response`,

    DELETE_TEAM_REQUEST: `${NAMESPACE}/delete/request`,
    DELETE_TEAM_RESPONSE: `${NAMESPACE}/delete/response`,

    LIST_TEAM_USERS_REQUEST: `${NAMESPACE}/listUsers/request`,
    LIST_TEAM_USERS_RESPONSE: `${NAMESPACE}/listUsers/response`,

    LIST_TEAM_LDAP_GROUPS_REQUEST: `${NAMESPACE}/listLdapGroups/request`,
    LIST_TEAM_LDAP_GROUPS_RESPONSE: `${NAMESPACE}/listLdapGroups/response`,

    REPLACE_TEAM_USERS_REQUEST: `${NAMESPACE}/replace/request`,
    REPLACE_TEAM_USERS_RESPONSE: `${NAMESPACE}/replace/response`,

    REPLACE_TEAM_LDAP_GROUPS_REQUEST: `${NAMESPACE}/replaceLdapGroups/request`,
    REPLACE_TEAM_LDAP_GROUPS_RESPONSE: `${NAMESPACE}/replaceLdapGroups/response`,

    RESET_TEAMS: `${NAMESPACE}/reset`
};

export const actions = {
    getTeam: (orgName: ConcordKey, teamName: ConcordKey): GetTeamRequest => ({
        type: actionTypes.GET_TEAM_REQUEST,
        orgName,
        teamName
    }),

    listTeams: (orgName: ConcordKey): ListTeamsRequest => ({
        type: actionTypes.LIST_TEAMS_REQUEST,
        orgName
    }),

    createTeam: (orgName: ConcordKey, entry: NewTeamEntry): CreateTeamRequest => ({
        type: actionTypes.CREATE_TEAM_REQUEST,
        orgName,
        entry
    }),

    renameTeam: (
        orgName: ConcordKey,
        teamId: ConcordId,
        teamName: ConcordKey
    ): RenameTeamRequest => ({
        type: actionTypes.RENAME_TEAM_REQUEST,
        orgName,
        teamId,
        teamName
    }),

    deleteTeam: (orgName: ConcordKey, teamName: ConcordKey): DeleteTeamRequest => ({
        type: actionTypes.DELETE_TEAM_REQUEST,
        orgName,
        teamName
    }),

    listTeamUsers: (orgName: ConcordKey, teamName: ConcordKey): ListTeamUsersRequest => ({
        type: actionTypes.LIST_TEAM_USERS_REQUEST,
        orgName,
        teamName
    }),

    listTeamLdapGroups: (orgName: ConcordKey, teamName: ConcordKey): ListTeamLdapGroupsRequest => ({
        type: actionTypes.LIST_TEAM_LDAP_GROUPS_REQUEST,
        orgName,
        teamName
    }),

    replaceUsers: (
        orgName: ConcordKey,
        teamName: ConcordKey,
        users: NewTeamUserEntry[]
    ): ReplaceTeamUsersRequest => ({
        type: actionTypes.REPLACE_TEAM_USERS_REQUEST,
        orgName,
        teamName,
        users
    }),

    replaceLdapGroups: (
        orgName: ConcordKey,
        teamName: ConcordKey,
        groups: NewTeamLdapGroupEntry[]
    ): ReplaceTeamLdapGroupsRequest => ({
        type: actionTypes.REPLACE_TEAM_LDAP_GROUPS_REQUEST,
        orgName,
        teamName,
        groups
    }),

    reset: () => ({
        type: actionTypes.RESET_TEAMS
    })
};

const getReducers = combineReducers<GetTeamState>({
    running: makeLoadingReducer([actionTypes.GET_TEAM_REQUEST], [actionTypes.TEAM_DATA_RESPONSE]),
    error: makeErrorReducer([actionTypes.GET_TEAM_REQUEST], [actionTypes.TEAM_DATA_RESPONSE]),
    response: makeResponseReducer(actionTypes.TEAM_DATA_RESPONSE, actionTypes.RESET_TEAMS)
});

const listReducers = combineReducers<ListTeamsState>({
    running: makeLoadingReducer([actionTypes.LIST_TEAMS_REQUEST], [actionTypes.TEAM_DATA_RESPONSE]),
    error: makeErrorReducer([actionTypes.LIST_TEAMS_REQUEST], [actionTypes.TEAM_DATA_RESPONSE]),
    response: makeResponseReducer(actionTypes.TEAM_DATA_RESPONSE, actionTypes.RESET_TEAMS)
});

const createReducers = combineReducers<CreateOrUpdateTeamState>({
    running: makeLoadingReducer(
        [actionTypes.CREATE_TEAM_REQUEST],
        [actionTypes.RESET_TEAMS, actionTypes.CREATE_TEAM_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.RESET_TEAMS, actionTypes.CREATE_TEAM_REQUEST],
        [actionTypes.CREATE_TEAM_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.CREATE_TEAM_RESPONSE, actionTypes.RESET_TEAMS)
});

const renameReducers = combineReducers<RenameTeamState>({
    running: makeLoadingReducer(
        [actionTypes.RENAME_TEAM_REQUEST],
        [actionTypes.RENAME_TEAM_RESPONSE]
    ),
    error: makeErrorReducer([actionTypes.RENAME_TEAM_REQUEST], [actionTypes.RENAME_TEAM_RESPONSE]),
    response: makeResponseReducer(
        actionTypes.RENAME_TEAM_RESPONSE,
        actionTypes.RENAME_TEAM_RESPONSE
    )
});

const deleteTeamReducers = combineReducers<DeleteTeamState>({
    running: makeLoadingReducer(
        [actionTypes.DELETE_TEAM_REQUEST],
        [actionTypes.DELETE_TEAM_RESPONSE]
    ),
    error: makeErrorReducer([actionTypes.DELETE_TEAM_REQUEST], [actionTypes.DELETE_TEAM_RESPONSE]),
    response: makeResponseReducer(actionTypes.DELETE_TEAM_RESPONSE, actionTypes.DELETE_TEAM_REQUEST)
});

const listUsersReducers = combineReducers<ListTeamUsersState>({
    running: makeLoadingReducer(
        [actionTypes.LIST_TEAM_USERS_REQUEST],
        [actionTypes.LIST_TEAM_USERS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.LIST_TEAM_USERS_REQUEST],
        [actionTypes.LIST_TEAM_USERS_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.LIST_TEAM_USERS_RESPONSE, actionTypes.RESET_TEAMS)
});

const listLdapGroupsReducers = combineReducers<ListTeamLdapGroupsState>({
    running: makeLoadingReducer(
        [actionTypes.LIST_TEAM_LDAP_GROUPS_REQUEST],
        [actionTypes.LIST_TEAM_LDAP_GROUPS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.LIST_TEAM_LDAP_GROUPS_REQUEST],
        [actionTypes.LIST_TEAM_LDAP_GROUPS_RESPONSE]
    ),
    response: makeResponseReducer(
        actionTypes.LIST_TEAM_LDAP_GROUPS_RESPONSE,
        actionTypes.RESET_TEAMS
    )
});

const replaceUsersReducers = combineReducers<ReplaceTeamUsersState>({
    running: makeLoadingReducer(
        [actionTypes.REPLACE_TEAM_USERS_REQUEST],
        [actionTypes.REPLACE_TEAM_USERS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.RESET_TEAMS, actionTypes.REPLACE_TEAM_USERS_REQUEST],
        [actionTypes.REPLACE_TEAM_USERS_RESPONSE]
    ),
    response: makeResponseReducer(actionTypes.REPLACE_TEAM_USERS_RESPONSE, actionTypes.RESET_TEAMS)
});

const replaceLdapGroupsReducers = combineReducers<ReplaceTeamLdapGroupsState>({
    running: makeLoadingReducer(
        [actionTypes.REPLACE_TEAM_LDAP_GROUPS_REQUEST],
        [actionTypes.REPLACE_TEAM_LDAP_GROUPS_RESPONSE]
    ),
    error: makeErrorReducer(
        [actionTypes.RESET_TEAMS, actionTypes.REPLACE_TEAM_LDAP_GROUPS_REQUEST],
        [actionTypes.REPLACE_TEAM_LDAP_GROUPS_RESPONSE]
    ),
    response: makeResponseReducer(
        actionTypes.REPLACE_TEAM_LDAP_GROUPS_RESPONSE,
        actionTypes.RESET_TEAMS
    )
});

export const reducers = combineReducers<State>({
    teamById: makeEntityByIdReducer(actionTypes.TEAM_DATA_RESPONSE), // TODO append LIST_TEAM_USERS_RESPONSE data?

    get: getReducers,
    list: listReducers,
    create: createReducers,
    rename: renameReducers,
    deleteTeam: deleteTeamReducers,

    listUsers: listUsersReducers,
    listLdapGroups: listLdapGroupsReducers,
    replaceUsers: replaceUsersReducers,
    replaceLdapGroups: replaceLdapGroupsReducers
});

export const selectors = {
    teamByName: (state: State, orgName: ConcordKey, teamName: ConcordKey) => {
        for (const id of Object.keys(state.teamById)) {
            const p = state.teamById[id];
            if (p.orgName === orgName && p.name === teamName) {
                return p;
            }
        }

        return;
    }
};

function* onGet({ orgName, teamName }: GetTeamRequest) {
    try {
        const response = yield call(apiGet, orgName, teamName);
        yield put({
            type: actionTypes.TEAM_DATA_RESPONSE,
            items: [response]
        });
    } catch (e) {
        yield handleErrors(actionTypes.TEAM_DATA_RESPONSE, e);
    }
}

function* onList({ orgName }: ListTeamsRequest) {
    try {
        const response = yield call(apiList, orgName);
        yield put({
            type: actionTypes.TEAM_DATA_RESPONSE,
            items: response
        });
    } catch (e) {
        yield handleErrors(actionTypes.TEAM_DATA_RESPONSE, e);
    }
}

function* onCreate({ orgName, entry }: CreateTeamRequest) {
    try {
        yield call(apiCreateOrUpdate, orgName, entry);
        yield put({
            type: actionTypes.CREATE_TEAM_RESPONSE
        });

        yield put(pushHistory(`/org/${orgName}/team/${entry.name}`));
    } catch (e) {
        yield handleErrors(actionTypes.CREATE_TEAM_RESPONSE, e);
    }
}

function* onRename({ orgName, teamId, teamName }: RenameTeamRequest) {
    try {
        yield call(apiRename, orgName, teamId, teamName);
        yield put({
            type: actionTypes.RENAME_TEAM_RESPONSE
        });

        yield put(pushHistory(`/org/${orgName}/team/${teamName}`));
    } catch (e) {
        yield handleErrors(actionTypes.RENAME_TEAM_RESPONSE, e);
    }
}

function* onDelete({ orgName, teamName }: DeleteTeamRequest) {
    try {
        yield call(apiDeleteTeam, orgName, teamName);
        yield put({
            type: actionTypes.DELETE_TEAM_RESPONSE
        });

        yield put(pushHistory(`/org/${orgName}/team/`));
    } catch (e) {
        yield handleErrors(actionTypes.DELETE_TEAM_RESPONSE, e);
    }
}

function* onListUsers({ orgName, teamName }: ListTeamUsersRequest) {
    try {
        const response = yield call(apiListUsers, orgName, teamName);
        yield put({
            type: actionTypes.LIST_TEAM_USERS_RESPONSE,
            items: response
        });
    } catch (e) {
        yield handleErrors(actionTypes.LIST_TEAM_USERS_RESPONSE, e);
    }
}

function* onListLdapGroups({ orgName, teamName }: ListTeamLdapGroupsRequest) {
    try {
        const response = yield call(apiListLdapGroups, orgName, teamName);
        yield put({
            type: actionTypes.LIST_TEAM_LDAP_GROUPS_RESPONSE,
            items: response
        });
    } catch (e) {
        yield handleErrors(actionTypes.LIST_TEAM_LDAP_GROUPS_RESPONSE, e);
    }
}

function* onReplaceUsers({ orgName, teamName, users }: ReplaceTeamUsersRequest) {
    try {
        yield call(apiAddUsers, orgName, teamName, true, users);
        yield put({
            type: actionTypes.REPLACE_TEAM_USERS_RESPONSE
        });

        yield put(actions.listTeamUsers(orgName, teamName));
    } catch (e) {
        yield handleErrors(actionTypes.REPLACE_TEAM_USERS_RESPONSE, e);
    }
}

function* onReplaceLdapGroups({ orgName, teamName, groups }: ReplaceTeamLdapGroupsRequest) {
    try {
        yield call(apiReplaceLdapGroups, orgName, teamName, true, groups);
        yield put({
            type: actionTypes.REPLACE_TEAM_LDAP_GROUPS_RESPONSE
        });

        yield put(actions.listTeamLdapGroups(orgName, teamName));
    } catch (e) {
        yield handleErrors(actionTypes.REPLACE_TEAM_LDAP_GROUPS_RESPONSE, e);
    }
}

export const sagas = function*() {
    yield all([
        takeLatest(actionTypes.GET_TEAM_REQUEST, onGet),
        takeLatest(actionTypes.LIST_TEAMS_REQUEST, onList),
        takeLatest(actionTypes.CREATE_TEAM_REQUEST, onCreate),
        takeLatest(actionTypes.RENAME_TEAM_REQUEST, onRename),
        takeLatest(actionTypes.DELETE_TEAM_REQUEST, onDelete),
        takeLatest(actionTypes.LIST_TEAM_USERS_REQUEST, onListUsers),
        takeLatest(actionTypes.REPLACE_TEAM_USERS_REQUEST, onReplaceUsers),
        takeLatest(actionTypes.LIST_TEAM_LDAP_GROUPS_REQUEST, onListLdapGroups),
        takeLatest(actionTypes.REPLACE_TEAM_LDAP_GROUPS_REQUEST, onReplaceLdapGroups)
    ]);
};
