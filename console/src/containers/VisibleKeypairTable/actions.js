// @flow
import type {ConcordKey, SortDirection} from "../../types";

export const actionTypes = {
    FETCH_KEYPAIR_LIST_REQUEST: "FETCH_KEYPAIR_LIST_REQUEST",
    FETCH_KEYPAIR_LIST_RESULT: "FETCH_KEYPAIR_LIST_RESULT",

    DELETE_KEYPAIR_REQUEST: "DELETE_KEYPAIR_REQUEST",
    DELETE_KEYPAIR_RESULT: "DELETE_KEYPAIR_RESULT"
};

export const fetchKeypairList = (sortBy: string, sortDir: SortDirection) => ({
    type: actionTypes.FETCH_KEYPAIR_LIST_REQUEST,
    sortBy,
    sortDir
});

export const deleteKeypair = (name: ConcordKey) => ({
    type: actionTypes.DELETE_KEYPAIR_REQUEST,
    name
});
