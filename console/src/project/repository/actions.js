/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */
// @flow

const NAMESPACE = 'repository';

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
