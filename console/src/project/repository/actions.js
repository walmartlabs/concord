// @flow

const NAMESPACE = "repository";

const types = {
    REPOSITORY_TEST_REQUEST: `${NAMESPACE}/test/request`,
    REPOSITORY_TEST_RESPONSE: `${NAMESPACE}/test/response`,
    REPOSITORY_TEST_RESET: `${NAMESPACE}/test/reset`,

    REPOSITORY_REFRESH_REQUEST: `${NAMESPACE}/refresh/request`,
    REPOSITORY_REFRESH_RESPONSE: `${NAMESPACE}/refresh/response`
};

export default types;

export const testRepository = (data: any) => ({
    type: types.REPOSITORY_TEST_REQUEST,
    data
});

export const refreshRepository = (orgName, projectName, repositoryName) => ({
    type: types.REPOSITORY_REFRESH_REQUEST,
    orgName,
    projectName,
    repositoryName
});

export const resetTest = () => ({
    type: types.REPOSITORY_TEST_RESET
});
