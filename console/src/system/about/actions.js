// @flow

const NAMESPACE = "about";

const types = {
    ABOUT_INFO_REQUEST: `${NAMESPACE}/request`,
    ABOUT_INFO_RESPONSE: `${NAMESPACE}/response`
};

export default types;

export const loadInfo = () => ({
    type: types.ABOUT_INFO_REQUEST
});
