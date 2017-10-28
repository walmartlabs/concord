// @flow

const NAMESPACE = "project/StartProjectPopup";

const types = {
    PROJECT_START_REQUEST: `${NAMESPACE}/start/request`,
    PROJECT_START_RESPONSE: `${NAMESPACE}/start/response`,
    PROJECT_START_RESET: `${NAMESPACE}/start/reset`
};

export default types;

export const startProject = (data: any) => ({
    type: types.PROJECT_START_REQUEST,
    data
});

export const resetStart = () => ({
    type: types.PROJECT_START_RESET
});
