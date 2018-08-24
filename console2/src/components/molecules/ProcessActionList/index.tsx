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
import { Table } from 'semantic-ui-react';
import { ConcordId } from '../../../api/common';

import { FormListEntry, FormRunAs } from '../../../api/process/form';

interface Props {
    instanceId: ConcordId;
    forms: FormListEntry[];
}

const renderRunAs = (v?: FormRunAs) => {
    if (!v) {
        return;
    }

    if (v.username) {
        return (
            <p>
                <b>Expects user:</b> {v.username}
            </p>
        );
    }

    if (v.ldap && v.ldap.group) {
        if (Array.isArray(v.ldap.group)) {
            return (
                <p>
                    <b>Expect groups:</b> {v.ldap.group.map((item) => '[' + item + ']')}
                </p>
            );
        } else {
            // For backward compatibility - Previously suspended forms still have `group` as string
            return (
                <p>
                    <b>Expects group:</b> [{v.ldap.group}]
                </p>
            );
        }
    }

    return;
};

class ProcessActionList extends React.PureComponent<Props> {
    render() {
        const { instanceId, forms } = this.props;

        return (
            <Table>
                <Table.Header>
                    <Table.Row>
                        <Table.HeaderCell collapsing={true}>Action</Table.HeaderCell>
                        <Table.HeaderCell>Description</Table.HeaderCell>
                    </Table.Row>
                </Table.Header>
                <Table.Body>
                    {forms.map(({ name, runAs }) => (
                        <Table.Row key={name}>
                            <Table.Cell singleLine={true}>
                                <Link to={`/process/${instanceId}/form/${name}/step`}>{name}</Link>
                            </Table.Cell>
                            <Table.Cell>
                                Form
                                {renderRunAs(runAs)}
                            </Table.Cell>
                        </Table.Row>
                    ))}
                </Table.Body>
            </Table>
        );
    }
}

export default ProcessActionList;
