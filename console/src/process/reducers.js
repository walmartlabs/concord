// @flow
import type {ConcordId} from "../types";
import {combineReducers} from "redux";
import types from "./actions";

const data = (state: any = null, {type, response, error}: any) => {
    switch (type) {
        case types.PROCESS_INFO_RESPONSE:
            if (error) {
                return state;
            }
            return response;
        default:
            return state;
    }
};

const loading = (state = false, {type}: any) => {
    switch (type) {
        case types.PROCESS_INFO_REQUEST:
            return true;
        case types.PROCESS_INFO_RESPONSE:
            return false;
        default:
            return state;
    }
};

const error = (state: any = null, {type, error, message}: any) => {
    switch (type) {
        case types.PROCESS_INFO_RESPONSE:
            if (!error) {
                return null;
            }
            return message;
        default:
            return state;
    }
};

const inFlight = (state: any = {}, {type, instanceId}: any) => {
    switch (type) {
        case types.PROCESS_KILL_REQUEST: {
            const o = {};
            o[instanceId] = true;
            return Object.assign({}, state, o);
        }
        case types.PROCESS_KILL_RESPONSE: {
            // TODO remove the key
            const o = {};
            o[instanceId] = false;
            return Object.assign({}, state, o);
        }
        default:
            return state;
    }
};

export default combineReducers({data, loading, error, inFlight});

export const getData = (state: any) => state.data;
export const isLoading = (state: any) => state.loading;
export const getError = (state: any) => state.error;
export const isInFlight = (state: any, instanceId: ConcordId) => state && state.inFlight[instanceId] === true;
