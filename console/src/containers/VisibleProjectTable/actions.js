// @flow
import type {ConcordKey, SortDirection} from "../../types";

export const actionTypes = {
    FETCH_PROJECT_LIST_REQUEST: "FETCH_PROJECT_LIST_REQUEST",
    FETCH_PROJECT_LIST_RESULT: "FETCH_PROJECT_LIST_RESULT",

    DELETE_PROJECT_REQUEST: "DELETE_PROJECT_REQUEST",
    DELETE_PROJECT_RESULT: "DELETE_PROJECT_RESULT"
};

export const fetchProjectList = (sortBy: string, sortDir: SortDirection) => ({
    type: actionTypes.FETCH_PROJECT_LIST_REQUEST,
    sortBy,
    sortDir
});

export const deleteProject = (name: ConcordKey) => ({
    type: actionTypes.DELETE_PROJECT_REQUEST,
    name
});
