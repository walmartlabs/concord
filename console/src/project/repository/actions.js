// @flow

const NAMESPACE = "repository";

const types = {
    REPOSITORY_TEST_REQUEST: `${NAMESPACE}/test/request`,
    REPOSITORY_TEST_RESPONSE: `${NAMESPACE}/test/response`,
    REPOSITORY_TEST_RESET: `${NAMESPACE}/test/reset`
};

export default types;

export const testRepository = (data: any) => ({
    type: types.REPOSITORY_TEST_REQUEST,
    data
});

export const resetTest = () => ({
    type: types.REPOSITORY_TEST_RESET
});
