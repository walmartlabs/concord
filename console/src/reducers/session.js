import {combineReducers} from "redux";
import actionTypes from "../actions/actionTypes";

const data = (state = {}, action) => {
    switch (action.type) {
        case actionTypes.session.LOGIN_SUCCESS:
            return action.response;
        default:
            return state;
    }
};

const loggedIn = (state = true, action) => {
    switch (action.type) {
        case actionTypes.session.LOGIN_SUCCESS:
            return true;
        case actionTypes.session.LOGIN_FAILURE:
            return false;
        default:
            return state;
    }
};

export default combineReducers({data, loggedIn});

export const getData = (state) => state.data;
export const getIsLoggedIn = (state) => state.loggedIn;
