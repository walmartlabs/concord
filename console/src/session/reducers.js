// @flow
import type {ConcordId} from "../types";

import types from "./actions";

export default (state: any = {}, {type, ...rest}: any) => {
    switch (type) {
        case types.SET_CURRENT_SESSION: {
            return chooseDefaultTeam({...rest});
        }
        case types.UPDATE_SESSION: {
            return chooseDefaultTeam(Object.assign({}, state, ...rest));
        }
        case types.CHANGE_TEAM: {
            const t = getTeam(state, rest.teamId);
            return Object.assign({}, state, {currentTeam: t});
        }
        default: {
            return state;
        }
    }
};

const chooseDefaultTeam = (state: any) => {
    const {teams} = state;

    if (!teams || teams.length === 0) {
        throw new Error("The current user is not in any team");
    }

    let t = teams[0];
    return Object.assign({}, state, {currentTeam: t});
};

const getTeam = (state: any, teamId: ConcordId) => {
    const {teams} = state;
    const found = teams.filter(t => t.id === teamId);
    if (!found || found.length <= 0) {
        throw new Error(`Team not found: ${teamId}`);
    }
    return found[0];
};

export const isLoggedIn = (state: any) => {
    const t = state.username;
    return t !== undefined && t !== null;
};

export const getDisplayName = (state: any) => state.displayName;

export const getDestination = (state: any) => state.destination;

export const getCurrentTeam = (state: any) => state.currentTeam;

export const getAvailableTeams = (state: any) => state.teams;