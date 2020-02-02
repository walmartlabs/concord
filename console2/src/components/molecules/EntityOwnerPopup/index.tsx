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
import { EntityOwner } from '../../../api/common';
import { Popup, Table } from 'semantic-ui-react';

interface Props {
    data: EntityOwner;
}

export default ({ data }: Props) => (
    <Popup trigger={<div>{data.username}</div>}>
        <Table size="small" collapsing={true} compact={true} definition={true} singleLine={true}>
            <Table.Body>
                <Table.Row>
                    <Table.Cell>ID</Table.Cell>
                    <Table.Cell>{data.id}</Table.Cell>
                </Table.Row>
                <Table.Row>
                    <Table.Cell>Display Name</Table.Cell>
                    <Table.Cell>{data.displayName ? data.displayName : '-'}</Table.Cell>
                </Table.Row>
                <Table.Row>
                    <Table.Cell>Domain</Table.Cell>
                    <Table.Cell>{data.userDomain ? data.userDomain : '-'}</Table.Cell>
                </Table.Row>
            </Table.Body>
        </Table>
    </Popup>
);
