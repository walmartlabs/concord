// @flow
import actionTypes from "./actionTypes";
import type {ConcordId, ConcordKey, SortDirection, FetchRange} from "../types";

// VisibleHistoryTable

export const fetchHistoryData = (sortBy: string, sortDir: SortDirection) => ({
    type: actionTypes.history.FETCH_HISTORY_DATA_REQUEST,
    sortBy,
    sortDir
});

export const killProc = (id: ConcordId) => ({
    type: actionTypes.history.KILL_PROC_REQUEST,
    id
});


// VisibleProjectTable

export const fetchProjectList = (sortBy: string, sortDir: SortDirection) => ({
    type: actionTypes.projectList.FETCH_PROJECT_LIST_REQUEST,
    sortBy,
    sortDir
});

export const deleteProject = (name: ConcordKey) => ({
    type: actionTypes.projectList.DELETE_PROJECT_REQUEST,
    name
});

// VisibleProjectForm

export const fetchProject = (name: ConcordKey) => ({
    type: actionTypes.project.FETCH_PROJECT_REQUEST,
    name
});

export const makeNewProject = () => ({
    type: actionTypes.project.MAKE_NEW_PROJECT
});

export const createProject = (data: mixed, resolve: Function, reject: Function) => ({
    type: actionTypes.project.CREATE_PROJECT_REQUEST,
    data,
    resolve,
    reject
});

export const updateProject = (name: ConcordKey, data: mixed, resolve: Function, reject: Function) => ({
    type: actionTypes.project.UPDATE_PROJECT_REQUEST,
    name,
    data,
    resolve,
    reject
});

export const fetchTemplateList = (sortBy: string, sortDir: SortDirection) => ({
    type: actionTypes.templateList.FETCH_TEMPLATE_LIST_REQUEST,
    sortBy,
    sortDir
});

// VisibleLogViewer

export const fetchLogData = (instanceId: ConcordId, fetchRange: FetchRange, reset: boolean) => ({
    type: actionTypes.log.FETCH_LOG_DATA_REQUEST,
    instanceId,
    fetchRange,
    reset
});

