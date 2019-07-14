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
import { Button, Dropdown } from 'semantic-ui-react';

import { Pagination } from '../../../state/data/processes';

const defaultDropDownValues = [50, 100, 500];

interface Props {
    filterProps?: Pagination;
    handleLimitChange?: (limit: any) => void;
    handleNext: () => void;
    handlePrev: () => void;
    handleFirst: () => void;
    disablePrevious?: boolean;
    disableNext?: boolean;
    disableFirst?: boolean;
    dropDownValues?: number[]; // Numbers
    maxValue?: number; // Maximum Items that could render
}

interface State {
    filterState: Pagination;
}

const toState = (data?: Pagination): State => {
    return { filterState: data || {} };
};

class PaginationToolBar extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = toState(this.props.filterProps);
    }

    render() {
        const { handleLimitChange, dropDownValues = defaultDropDownValues, maxValue } = this.props;

        return (
            <>
                {handleLimitChange !== undefined && (
                    <Dropdown
                        compact={true}
                        options={dropDownValues.map((value) => ({
                            text: value,
                            value,
                            disabled: maxValue ? value >= maxValue : false
                        }))}
                        value={this.state.filterState.limit}
                        defaultValue={dropDownValues[0]}
                        selection={true}
                        basic={true}
                        fluid={false}
                        onChange={(v, data) => handleLimitChange(data.value)}
                    />
                )}
                <Button.Group>
                    <Button
                        basic={true}
                        icon="angle double left"
                        disabled={this.props.disableFirst}
                        onClick={() => this.props.handleFirst()}
                    />
                    <Button
                        basic={true}
                        icon="angle left"
                        disabled={this.props.disablePrevious}
                        onClick={() => this.props.handlePrev()}
                    />
                    <Button
                        basic={true}
                        icon="angle right"
                        disabled={this.props.disableNext}
                        onClick={() => this.props.handleNext()}
                    />
                </Button.Group>
            </>
        );
    }
}

export default PaginationToolBar;
