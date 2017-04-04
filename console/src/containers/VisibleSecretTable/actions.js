// @flow
import type {ConcordKey, SortDirection} from "../../types";

export const actionTypes = {
    FETCH_SECRET_LIST_REQUEST: "FETCH_SECRET_LIST_REQUEST",
    FETCH_SECRET_LIST_RESULT: "FETCH_SECRET_LIST_RESULT",

    DELETE_SECRET_REQUEST: "DELETE_SECRET_REQUEST",
    DELETE_SECRET_RESULT: "DELETE_SECRET_RESULT"
};

export const fetchSecretList = (sortBy: string, sortDir: SortDirection) => ({
    type: actionTypes.FETCH_SECRET_LIST_REQUEST,
    sortBy,
    sortDir
});

export const deleteSecret = (name: ConcordKey) => ({
    type: actionTypes.DELETE_SECRET_REQUEST,
    name
});
