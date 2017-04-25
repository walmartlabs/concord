// @flow

const NAMESPACE = "login";

const types = {
    LOGIN_REQUEST: `${NAMESPACE}/request`,
    LOGIN_RESPONSE: `${NAMESPACE}/response`,
    LOGIN_REFRESH: `${NAMESPACE}/refresh`
};

export default types;

export const doLogin = (username: string, password: string) => ({
    type: types.LOGIN_REQUEST,
    username,
    password
});

export const doRefresh = () => ({
    type: types.LOGIN_REFRESH
});