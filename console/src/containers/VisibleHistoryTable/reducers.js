import {combineReducers} from "redux";
import {actionTypes} from "./actions";
import * as common from "../../reducers/common";

const rows = common.makeListRowsReducer(actionTypes.FETCH_HISTORY_DATA_RESULT);
const loading = common.makeBooleanTriggerReducer(actionTypes.FETCH_HISTORY_DATA_REQUEST, actionTypes.FETCH_HISTORY_DATA_RESULT);
const error = common.makeErrorReducer(actionTypes.FETCH_HISTORY_DATA_REQUEST, actionTypes.FETCH_HISTORY_DATA_RESULT);
const lastQuery = common.makeListLastQueryReducer(actionTypes.FETCH_HISTORY_DATA_REQUEST);

const inFlightIds = (state = [], action) => {
    switch (action.type) {
        case actionTypes.KILL_PROC_REQUEST:
            return [...state, action.id];
        case actionTypes.KILL_PROC_RESULT:
            return state.filter((v) => v !== action.id);
        default:
            return state;
    }
};

export default combineReducers({rows, loading, error, lastQuery, inFlightIds});

export const getRows = (state) => state.rows;
export const getIsLoading = (state) => state.loading;
export const getError = (state) => state.error;
export const getLastQuery = (state) => state.lastQuery;
export const isInFlight = (state, id) => state.inFlightIds.includes(id);
