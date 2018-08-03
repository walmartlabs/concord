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
import { Link } from 'react-router-dom';
import { getStatusSemanticColor, ProcessStatus } from '../../../api/process';
import { queryParams } from '../../../api/common';
import { State as SessionState } from '../../../state/session';
import { connect } from 'react-redux';

export interface StatusCount {
    status: ProcessStatus;
    count: number;
}

interface Props {
    items: StatusCount[];
}

interface StateProps {
    userName: string;
}

const renderItem = (initiator: string, status: ProcessStatus, count: number) => {
    return (
        <div className={'statistic ' + getStatusSemanticColor(status)} key={status}>
            <div className="value">{count}</div>
            <div className="label">
                {count > 0 ? (
                    <Link to={`/process?${queryParams({ status, initiator })}`}>{status}</Link>
                ) : (
                    status
                )}
            </div>
        </div>
    );
};

class UserProcessStats extends React.PureComponent<Props & StateProps> {
    render() {
        const { items, userName } = this.props;

        return (
            <div className="ui small statistics five column row">
                {items.map((v) => renderItem(userName, v.status, v.count))}
            </div>
        );
    }
}

const mapStateToProps = ({ session }: { session: SessionState }): StateProps => ({
    userName: session.user.username!
});

export default connect(mapStateToProps)(UserProcessStats);
