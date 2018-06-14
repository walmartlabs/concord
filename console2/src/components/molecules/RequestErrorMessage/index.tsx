/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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
import { Link } from "react-router-dom";
import { Message } from 'semantic-ui-react';

import { RequestError } from '../../../api/common';

interface Props {
    error: RequestError;
}

export default class extends React.PureComponent<Props> {
    render() {
        const { error } = this.props;

        if (!error) {
            return <p>No error</p>;
        }

        const details = error.details && error.details.length > 0 ? error.details : undefined;

        return (
            <Message negative={true}>
                {error.message && <Message.Header>{error.message}</Message.Header>}
                {details && <p>{details}</p>}
                {error.instanceId && <p><Link to={`/process/${error.instanceId}/log`}>Open the process log</Link></p>}
            </Message>
        );
    }
}
