/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import * as React from 'react';
import { useContext, useEffect } from 'react';
import { Redirect } from 'react-router';

import { RequestError } from '../../../api/common';
import { RequestErrorMessage } from '../../molecules';
import { logout, UserSessionContext } from '../../../session';

interface Props {
    error: RequestError;
}

export default ({ error }: Props) => {
    const session = useContext(UserSessionContext);

    useEffect(() => {
        const doIt = async () => {
            if (error && error.status === 401) {
                await logout(session, false);
                return <Redirect to={'/unauthorized'} />;
            }
        };

        doIt();
    }, [error, session]);

    return <RequestErrorMessage error={error} />;
};
