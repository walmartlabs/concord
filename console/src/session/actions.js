// @flow
const NAMESPACE = "session";

const types = {
    SET_CURRENT_SESSION: `${NAMESPACE}/setCurrent`,
    UPDATE_SESSION: `${NAMESPACE}/update`,
    CHECK_AUTH: `${NAMESPACE}/checkAuth`,
    LOGOUT: `${NAMESPACE}/logout`
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

export const checkAuth = () => ({
    type: types.CHECK_AUTH
});

export const logOut = () => ({
    type: types.LOGOUT
});
