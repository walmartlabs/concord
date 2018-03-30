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
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Header, List, Loader } from 'semantic-ui-react';
import reducers from './reducers';
import * as selectors from './reducers';
import sagas from './sagas';
import * as actions from './actions';

class AboutPage extends Component {
    componentDidMount() {
        const { loadData } = this.props;
        loadData();
    }

    render() {
        const { serverVersion, loading } = this.props;

        if (loading) {
            return <Loader active />;
        }

        return (
            <div>
                <Header as="h3">About</Header>

                <List divided relaxed>
                    <List.Item>
                        <List.Icon size="large" name="server" verticalAlign="middle" />
                        <List.Content>
                            <List.Header>Server</List.Header>
                            <List.Description>version: {serverVersion}</List.Description>
                        </List.Content>
                    </List.Item>
                    <List.Item>
                        <List.Icon size="large" name="browser" verticalAlign="middle" />
                        <List.Content>
                            <List.Header>Console</List.Header>
                            <List.Description>
                                version: {process.env.REACT_APP_CONCORD_VERSION}
                            </List.Description>
                        </List.Content>
                    </List.Item>
                </List>
            </div>
        );
    }
}

const mapStateToProps = ({ about }) => ({
    serverVersion: selectors.getInfo(about).version,
    loading: selectors.isLoading(about)
});

const mapDispatchToProps = (dispatch) => ({
    loadData: () => dispatch(actions.loadInfo())
});

export default connect(mapStateToProps, mapDispatchToProps)(AboutPage);

export { reducers, sagas };
