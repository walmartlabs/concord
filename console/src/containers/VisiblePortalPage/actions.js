// @flow

export const actionTypes = {
    START_PROCESS_REQUEST: "START_PROCESS_REQUEST",
    START_PROCESS_RESULT: "START_PROCESS_RESULT",
};

export const startProcess = (entryPoint: string) => ({
    type: actionTypes.START_PROCESS_REQUEST,
    entryPoint: entryPoint
});

