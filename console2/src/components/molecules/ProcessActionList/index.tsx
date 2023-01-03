/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import { Button, Table } from 'semantic-ui-react';
import { ConcordId } from '../../../api/common';

import { FormListEntry, FormRunAs } from '../../../api/process/form';

interface Props {
    instanceId: ConcordId;
    forms: FormListEntry[];
    onOpenWizard: () => void;
}

// this ugly mess handles all our different ways of specifying LDAP groups in `runAs` form parameter
// see also server/impl/src/main/java/com/walmartlabs/concord/server/process/form/FormUtils.java getRunAsLdapGroups method

const groupToString = (item: { group: string } | string): string => {
    if (typeof item === 'string') {
        return item;
    }
    return item.group;
};

const renderStringOrArrayOfStrings = (item: string | string[]): string => {
    if (typeof item === 'string') {
        return item;
    }
    return item.join(',');
};

const renderExpectedGroups = (items: string | string[]) => (
    <p>
        <b>Expect groups:</b> [{renderStringOrArrayOfStrings(items)}]
    </p>
);

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

    if (v.ldap) {
        if (Array.isArray(v.ldap)) {
            return renderExpectedGroups(v.ldap.map(groupToString));
        } else if (v.ldap.group) {
            // for backward compatibility - previously suspended forms still have `group` as string
            return renderExpectedGroups(v.ldap.group);
        }
    }

    return;
};

class ProcessActionList extends React.PureComponent<Props> {
    render() {
        const { instanceId, forms, onOpenWizard } = this.props;

        return (
            <>
                <Button
                    id="formWizardButton"
                    onClick={() => onOpenWizard()}
                    content="Form Wizard"
                    color="blue"
                    compact={true}
                />
                <Table>
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell collapsing={true}>Form Action</Table.HeaderCell>
                            <Table.HeaderCell>Description</Table.HeaderCell>
                        </Table.Row>
                    </Table.Header>
                    <Table.Body>
                        {forms.map(({ name, runAs }) => (
                            <Table.Row key={name}>
                                <Table.Cell singleLine={true}>
                                    <Link to={`/process/${instanceId}/form/${name}/step`}>
                                        <Button
                                            content={`${name}`}
                                            compact={true}
                                            basic={true}
                                            color="red"
                                            size="small"
                                        />
                                    </Link>
                                </Table.Cell>
                                <Table.Cell>
                                    Form
                                    {renderRunAs(runAs)}
                                </Table.Cell>
                            </Table.Row>
                        ))}
                    </Table.Body>
                </Table>
            </>
        );
    }
}

export default ProcessActionList;
