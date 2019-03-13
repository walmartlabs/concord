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
import { distanceInWords, distanceInWordsToNow } from 'date-fns';
import React, { useContext } from 'react';
import { Link } from 'react-router-dom';
import { Divider } from 'semantic-ui-react';
import { parse as parseDate } from 'date-fns';

import { ProcessEntry } from '../../../../api/process';
import { Truncate } from '../../../atoms';
import { Label, Status } from '../shared/Labels';
import { LeftWrap, ListItem } from './styles';

interface Props {
    process: ProcessEntry;
}

export default ({ process }: Props) => {
    return (
        <LeftWrap maxWidth={300}>
            <ListItem>
                <div>
                    <Label>
                        Process:{' '}
                        <Link to={`/process/${process.instanceId}`}>
                            <Truncate text={process.instanceId} />
                        </Link>
                    </Label>
                </div>

                {/* Show repository name if it exists */}
                {process.repoName && (
                    <div>
                        <Label>Repo: </Label>
                        <Link
                            to={`/org/${process.orgName}/project/${
                                process.projectName
                            }/repository/${process.repoName}`}>
                            {process.repoName}
                        </Link>
                    </div>
                )}
                {Object.keys(process.meta!)
                    .filter((value) => {
                        // Weed out non-relevant metadata
                        if (value.includes('_system')) {
                            return false;
                        }
                        // Found real metadata
                        return true;
                    })
                    .map((key) => {
                        return (
                            <div key={key}>
                                <Label>{key}</Label>
                                {process.meta && `: ${process.meta[key]}`}
                            </div>
                        );
                    })}
                <Divider />
                <div>
                    <Label>Current Status: </Label>
                    <Status>{process.status}</Status>
                </div>

                {/* Last update time, tooltip on mouse hover */}
                <div title={process.lastUpdatedAt}>
                    <Label>Last update: </Label>
                    <Status>{distanceInWordsToNow(parseDate(process.lastUpdatedAt))}</Status>
                </div>
            </ListItem>
        </LeftWrap>
    );
};
