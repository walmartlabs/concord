// @flow
import type {ConcordKey} from "../../types";

export const actionTypes = {
    FETCH_PROJECT_REQUEST: "FETCH_PROJECT_REQUEST",
    FETCH_PROJECT_RESULT: "FETCH_PROJECT_RESULT",

    UPDATE_PROJECT_REQUEST: "UPDATE_PROJECT_REQUEST",
    CREATE_PROJECT_REQUEST: "CREATE_PROJECT_REQUEST",
    MAKE_NEW_PROJECT: "MAKE_NEW_PROJECT"
};

export const fetchProject = (name: ConcordKey) => ({
    type: actionTypes.FETCH_PROJECT_REQUEST,
    name
});

export const makeNewProject = () => ({
    type: actionTypes.MAKE_NEW_PROJECT
});

export const createProject = (data: mixed, resolve: Function, reject: Function) => ({
    type: actionTypes.CREATE_PROJECT_REQUEST,
    data,
    resolve,
    reject
});

export const updateProject = (name: ConcordKey, data: mixed, resolve: Function, reject: Function) => ({
    type: actionTypes.UPDATE_PROJECT_REQUEST,
    name,
    data,
    resolve,
    reject
});
