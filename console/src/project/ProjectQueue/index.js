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
import { connect } from 'react-redux';
import { Message } from 'semantic-ui-react';
import ProcessTable from '../../process/ProcessTable';
import * as types from '../../process/actions';

export class ProjectQueue extends React.Component {
    componentDidMount() {
        this.load();
    }

    componentDidUpdate(prevProps) {
        const { projectId: prevId } = prevProps;
        const { projectId: currentId } = this.props;

        if (!prevId || prevId !== currentId) {
            this.load();
        }
    }

    load() {
        const { fetchProjectProcessesFn, projectId } = this.props;
        if (projectId) fetchProjectProcessesFn(projectId);
    }

    render() {
        const { processes } = this.props;
        return processes && processes.length ? (
            <ProcessTable processes={processes} />
        ) : (
            <Message>
                <Message.Header>No processes found</Message.Header>
                <p>All recently ran processes will show up here.</p>
            </Message>
        );
    }
}

const mapStateToProps = ({ project, process }, { projectId }) => ({
    processes: projectId ? process.byProject : null
});

const mapDispatchToProps = (dispatch) => ({
    fetchProjectProcessesFn: (projectId) => {
        dispatch(types.requestProjectProcesses(projectId));
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(ProjectQueue);
