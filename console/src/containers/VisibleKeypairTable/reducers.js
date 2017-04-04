import {combineReducers} from "redux";
import * as common from "../../reducers/common";
import {actionTypes} from "./actions";

const rows = common.makeListRowsReducer(actionTypes.FETCH_KEYPAIR_LIST_RESULT);
const loading = common.makeBooleanTriggerReducer(actionTypes.FETCH_KEYPAIR_LIST_REQUEST, actionTypes.FETCH_KEYPAIR_LIST_RESULT);
const error = common.makeErrorReducer(actionTypes.FETCH_KEYPAIR_LIST_REQUEST, actionTypes.FETCH_KEYPAIR_LIST_RESULT);
const lastQuery = common.makeListLastQueryReducer(actionTypes.FETCH_KEYPAIR_LIST_REQUEST);

const inFlight = (state = [], action) => {
    switch (action.type) {
        case actionTypes.DELETE_KEYPAIR_REQUEST:
            return [...state, action.name];
        case actionTypes.DELETE_KEYPAIR_RESULT:
            return state.filter((v) => v !== action.name);
        default:
            return state;
    }
};

export default combineReducers({rows, loading, error, lastQuery, inFlight});

export const getRows = (state) => state.rows;
export const getIsLoading = (state) => state.loading;
export const getError = (state) => state.error;
export const getLastQuery = (state) => state.lastQuery;
export const isInFlight = (state, name) => state.inFlight.includes(name);