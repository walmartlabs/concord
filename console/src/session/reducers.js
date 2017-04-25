// @flow
import types from "./actions";

export default (state: any = {}, action: any) => {
    switch (action.type) {
        case types.SET_CURRENT_SESSION:
            delete action.type;
            return {...action};
        case types.UPDATE_SESSION:
            delete action.type;
            return Object.assign({}, state, ...action);
        default:
            return state;
    }
};

export const isLoggedIn = (state: any) => {
    const t = state.username;
    return t !== undefined && t !== null;
};

export const getDisplayName = (state: any) => state.displayName;