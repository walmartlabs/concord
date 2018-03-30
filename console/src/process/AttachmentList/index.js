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
// @ts-check

import React, { Component } from 'react';
import { getProcessAttachmentsList } from './api';
import { Grid, Label, Segment, Header, List, Loader } from 'semantic-ui-react';

export class AttachmentList extends Component {
    constructor() {
        super();
        this.state = {
            loading: true,
            data: [],
            error: null
        };
    }

    componentWillMount() {
        getProcessAttachmentsList(this.props.instanceId)
            .then((value) => {
                this.setState({ data: value, loading: false, error: null });
            })
            .catch((reason) => {
                this.setState({ error: reason.message, loading: false });
            });
    }

    render() {
        const { instanceId } = this.props;
        const { loading, data, error } = this.state;

        if (loading) {
            return <Segment loading={true} basic />;
        } else {
            return (
                <div>
                    {data && <Header as="h4">Attachment List</Header>}
                    {data && (
                        <List>
                            {data.map((value, i) => (
                                <List.Item
                                    as="a"
                                    href={`/api/v1/process/${instanceId}/attachment/${value}`}
                                    download={`${value}`}
                                    key={i}>
                                    {value}
                                </List.Item>
                            ))}
                        </List>
                    )}
                    {error && <div>{error}</div>}
                </div>
            );
        }
    }
}
