// @flow
import type {ConcordId} from "../types";

const NAMESPACE = "session";

const types = {
    SET_CURRENT_SESSION: `${NAMESPACE}/setCurrent`,
    UPDATE_SESSION: `${NAMESPACE}/update`,
    CHECK_AUTH: `${NAMESPACE}/checkAuth`,
    LOGOUT: `${NAMESPACE}/logout`,
    CHANGE_TEAM: `${NAMESPACE}/changeTeam`
};

export default types;

export const setCurrent = (data: any) => ({
    type: types.SET_CURRENT_SESSION,
    ...data
});

export const update = (data: any) => ({
    type: types.UPDATE_SESSION,
    ...data
});

export const checkAuth = (destination: any) => ({
    type: types.CHECK_AUTH,
    destination
});

export const logOut = () => ({
    type: types.LOGOUT
});

export const changeTeam = (teamId: ConcordId) => ({
    type: types.CHANGE_TEAM,
    teamId
});