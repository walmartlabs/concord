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
import { Dropdown, Table } from 'semantic-ui-react';

import { ConcordId } from '../../../../api/common';
import { memo } from 'react';

export interface PlaybookEntry {
    value: ConcordId;
    text: string;
}

export interface ExternalProps {
    currentValue?: ConcordId;
    options?: PlaybookEntry[];
    onPlaybookChange: (flowCorrelationId: ConcordId) => void;
}

const PlaybookChooser = memo(({ currentValue, options, onPlaybookChange }: ExternalProps) => {
    return (
        <Table basic={'very'} compact={true} singleLine={true}>
            <Table.Body>
                <Table.Row>
                    <Table.Cell
                        style={{ fontWeight: 'bold' }}
                        width={1}
                        className={currentValue ? '' : 'loading'}>
                        Playbook:
                    </Table.Cell>
                    <Table.Cell width={15}>
                        <Dropdown
                            selection={options !== undefined ? true : undefined}
                            value={currentValue}
                            options={options}
                            onChange={(ev, data) => onPlaybookChange(data.value as ConcordId)}
                            disabled={currentValue === undefined}
                            style={{ width: '100%' }}
                        />
                    </Table.Cell>
                </Table.Row>
            </Table.Body>
        </Table>
    );
});

export default PlaybookChooser;
