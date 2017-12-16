// @flow
import type {ConcordId} from "../types";
import Store from "store";

import types from "./actions";

export default (state: any = {}, {type, ...rest}: any) => {
    switch (type) {
        case types.SET_CURRENT_SESSION: {
            return chooseDefaultOrg({...rest});
        }
        case types.UPDATE_SESSION: {
            return chooseDefaultOrg(Object.assign({}, state, ...rest));
        }
        case types.CHANGE_ORG: {
            return chooseOrg(state, rest.orgId);
        }
        default: {
            return state;
        }
    }
};

const chooseDefaultOrg = (state: any) => {
    const {orgs} = state;

    if (!orgs || orgs.length === 0) {
        throw new Error("The current user is not in any team");
    }

    const prevOrg = Store.get("concord-org");

    let o = prevOrg ? getOrg(state, prevOrg) : orgs[0];
    if (!o) {
        o = orgs[0];
    }

    return Object.assign({}, state, {currentOrg: o});
};

const chooseOrg = (state: any, orgId: ConcordId) => {
    const t = getOrg(state, orgId);
    Store.set("concord-org", orgId);
    return Object.assign({}, state, {currentOrg: t});
};

const getOrg = (state: any, orgId: ConcordId) => {
    const {orgs} = state;

    const found = orgs.filter(t => t.id === orgId);
    if (!found || found.length <= 0) {
        return;
    }

    return found[0];
};

export const isLoggedIn = (state: any) => {
    const u = state.username;
    return u !== undefined && u !== null;
};

export const getDisplayName = (state: any) => state.displayName;

export const getDestination = (state: any) => state.destination;

export const getCurrentOrg = (state: any) => state.currentOrg;

export const getAvailableOrgs = (state: any) => state.orgs;