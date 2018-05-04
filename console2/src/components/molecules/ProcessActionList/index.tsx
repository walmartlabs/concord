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
        return (
            <p>
                <b>Expects group:</b> {v.ldap.group}
            </p>
        );
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
                    {forms.map(({ formInstanceId, name, runAs }) => (
                        <Table.Row key={formInstanceId}>
                            <Table.Cell singleLine={true}>
                                <Link to={`/process/${instanceId}/form/${formInstanceId}/step`}>
                                    {name}
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
        );
    }
}

export default ProcessActionList;
