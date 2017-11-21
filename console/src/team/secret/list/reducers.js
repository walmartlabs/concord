// @flow
import {combineReducers} from "redux";
import * as common from "../../../reducers/common";
import types from "./actions";

const rows = (state = [], {type, error, response}) => {
    switch (type) {
        case types.USER_SECRET_LIST_RESPONSE: {
            if (error) {
                return state;
            }
            return response;
        }
        default: {
            return state;
        }
    }
};

const loading = common.booleanTrigger(types.USER_SECRET_LIST_REQUEST, types.USER_SECRET_LIST_RESPONSE);
const error = common.error(types.USER_SECRET_LIST_RESPONSE);

const deleteError = (state = null, action) => {
    switch (action.type) {
        case types.USER_SECRET_LIST_REQUEST:
        case types.USER_SECRET_LIST_RESPONSE: {
            return null;
        }
        case types.USER_SECRET_DELETE_RESPONSE: {
            if (!action.error) {
                return null;
            }
            return action.message;
        }
        default: {
            return state;
        }
    }
};

const inFlight = (state = [], action) => {
    switch (action.type) {
        case types.USER_SECRET_DELETE_REQUEST: {
            const v = `${action.teamName}/${action.name}`;
            return [...state, v];
        }
        case types.USER_SECRET_DELETE_RESPONSE: {
            const x = `${action.teamName}/${action.name}`;
            return state.filter((y) => y !== x);
        }
        default: {
            return state;
        }
    }
};

const publicKey = (state = null, action) => {
    switch (action.type) {
        case types.USER_SECRET_PUBLICKEY_REQUEST: {
            return null;
        }
        case types.USER_SECRET_PUBLICKEY_RESPONSE: {
            return action.publicKey || null;
        }
        default: {
            return state;
        }
    }
};

const publicKeyError = (state = null, action) => {
    switch (action.type) {
        case types.USER_SECRET_PUBLICKEY_REQUEST:
            return null;
        case types.USER_SECRET_PUBLICKEY_RESPONSE:
            return action.error || null;
        default:
            return state;
    }
};


export default combineReducers({rows, loading, error, inFlight, deleteError, publicKey, publicKeyError});

export const getRows = (state: any) => state.rows;
export const getIsLoading = (state: any) => state.loading;
export const getError = (state: any) => state.error;
export const getDeleteError = (state: any) => state.deleteError;
export const isInFlight = (state: any, teamName: any, name: any) => state.inFlight.includes(`${teamName}/${name}`);
export const getPublicKeyError = (state: any) => state.publicKeyError;
export const getPublicKey = (state: any) => state.publicKey;