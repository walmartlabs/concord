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
import { Redirect } from 'react-router';

import { RequestError } from '../../../api/common';
import { RequestErrorMessage } from '../../molecules';
import { useLocation } from 'react-router-dom';

interface Props {
    error: RequestError;
}

export default ({ error }: Props) => {
    const location = useLocation();

    if (error && error.status === 401) {
        return (
            <Redirect
                to={{
                    pathname: '/login',
                    state: {
                        from: location
                    }
                }}
            />
        );
    }

    return <RequestErrorMessage error={error} />;
};
