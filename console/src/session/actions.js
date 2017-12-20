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
import type {ConcordId} from "../types";

const NAMESPACE = "session";

const types = {
    SET_CURRENT_SESSION: `${NAMESPACE}/setCurrent`,
    UPDATE_SESSION: `${NAMESPACE}/update`,
    CHECK_AUTH: `${NAMESPACE}/checkAuth`,
    LOGOUT: `${NAMESPACE}/logout`,
    CHANGE_ORG: `${NAMESPACE}/changeOrg`
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

export const checkAuth = (destination: any) => ({
    type: types.CHECK_AUTH,
    destination
});

export const logOut = () => ({
    type: types.LOGOUT
});

export const changeOrg = (orgId: ConcordId) => ({
    type: types.CHANGE_ORG,
    orgId
});
