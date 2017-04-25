// @flow

const NAMESPACE = "process/portal";

const types = {
    PROCESS_PORTAL_START_REQUEST: `${NAMESPACE}/start/request`,
    PROCESS_PORTAL_START_RESPONSE: `${NAMESPACE}/start/response`
};

export default types;

export const startProcess = (entryPoint: string) => ({
    type: types.PROCESS_PORTAL_START_REQUEST,
    entryPoint: entryPoint
});

