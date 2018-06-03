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
import { Icon } from 'semantic-ui-react';
import { version as apiVersion } from '../../../api/server';

interface State {
    loading: boolean;
    version?: string;
}

class ServerVersion extends React.Component<{}, State> {
    constructor(props: {}) {
        super(props);
        this.state = { loading: false };
    }

    componentDidMount() {
        this.setState({ loading: true });
        apiVersion()
            .then((v) => this.setState({ loading: false, version: v.version }))
            .catch(() => this.setState({ loading: false, version: 'error' }));
    }

    render() {
        const { loading, version } = this.state;

        if (loading || !version) {
            return <Icon name="circle notched" loading={true} />;
        }

        return version;
    }
}

export default ServerVersion;
