// @flow
import types from "./actions";

export default (state: any = {}, action: any) => {
    switch (action.type) {
        case types.SET_CURRENT_SESSION:
            delete action.type;
            return selectTeam({...action});
        case types.UPDATE_SESSION:
            delete action.type;
            return selectTeam(Object.assign({}, state, action.params));
        default:
            return state;
    }
};

const selectTeam = (state: any) => {
    const {currentTeam, teams} = state;

    if (!teams || teams.length === 0) {
        throw new Error("The current user is not in any team");
    }

    let t = currentTeam;
    if (t) {
        let found = teams.filter(x => x.name === x).length !== 0;
        if (!found) {
            t = null;
        }
    }

    if (!t) {
        t = teams[0];
        console.debug("selectTeam -> current team: ", t);
    }

    return Object.assign({}, state, {currentTeam: t});
};

export const isLoggedIn = (state: any) => {
    const t = state.username;
    return t !== undefined && t !== null;
};

export const getDisplayName = (state: any) => state.displayName;

export const getDestination = (state: any) => state.destination;

export const getCurrentTeamName = (state: any) => {
    const t = state.currentTeam;
    return t ? t.name : undefined;
};