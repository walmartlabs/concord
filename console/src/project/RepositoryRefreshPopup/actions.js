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
import type {ConcordKey} from "../../types";

const NAMESPACE = "repository";

const types = {
    REPOSITORY_REFRESH_REQUEST: `${NAMESPACE}/refresh/request`,
    REPOSITORY_REFRESH_RESPONSE: `${NAMESPACE}/refresh/response`,
    REPOSITORY_REFRESH_RESET: `${NAMESPACE}/refresh/reset`
};

export default types;

export const refreshRepository = (orgName: ConcordKey, projectName: ConcordKey, repositoryName: ConcordKey) => ({
    type: types.REPOSITORY_REFRESH_REQUEST,
    orgName,
    projectName,
    repositoryName
});

export const resetRefresh = () => ({
    type: types.REPOSITORY_REFRESH_RESET
});