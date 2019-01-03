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
import { FullBar, Item } from './styles';
import RefreshButton from '../../../atoms/RefreshButton';
import { Pagination } from '../../../../components/molecules';
import CheckpointViewContainer from '../CheckpointViewContainer';

const ActionBar: React.SFC<{}> = () => (
    <CheckpointViewContainer>
        {({
            setPageLimit,
            isFirstPage,
            refreshProcessData,
            loadingData,
            orgId,
            projectId,
            getPaginationAsString,
            currentPage,
            setCurrentPage,
            limitPerPage,
            processes
        }) => (
            <FullBar>
                <Item>
                    <RefreshButton
                        loading={loadingData}
                        clickAction={() => {
                            refreshProcessData({
                                orgId,
                                projectId,
                                limit: limitPerPage,
                                offset: (currentPage - 1) * limitPerPage
                            });
                        }}
                    />
                </Item>
                <Item>{getPaginationAsString()}</Item>
                <Item style={{ marginLeft: 'auto' }}>
                    <Pagination
                        filterProps={{}}
                        handleLimitChange={(limit) => {
                            setPageLimit(limit);
                            refreshProcessData({
                                orgId,
                                projectId,
                                limit,
                                offset: 0
                            });
                        }}
                        handleNext={() => {
                            setCurrentPage(currentPage + 1);
                            refreshProcessData({
                                orgId,
                                projectId,
                                limit: limitPerPage,
                                offset: currentPage * limitPerPage // offset calc e.g. 4 -> 5 (4 * 10 = 40)
                            });
                        }}
                        handlePrev={() => {
                            setCurrentPage(currentPage - 1);
                            refreshProcessData({
                                orgId,
                                projectId,
                                limit: limitPerPage,
                                offset: (currentPage - 1 - 1) * limitPerPage // offset calc e.g. 4 -> 3 (4 - 2 = 2 : 2 * 10 = 20)
                            });
                        }}
                        handleFirst={() => {
                            setCurrentPage(1);
                            refreshProcessData({
                                orgId,
                                projectId,
                                limit: limitPerPage,
                                offset: 0
                            });
                        }}
                        disablePrevious={isFirstPage()}
                        disableNext={processes ? processes.length < limitPerPage : false}
                        disableFirst={isFirstPage()}
                        dropDownValues={[10, 25, 50]}
                    />
                </Item>
            </FullBar>
        )}
    </CheckpointViewContainer>
);

export default ActionBar;
