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
import { Button, Dropdown, DropdownItemProps } from 'semantic-ui-react';

const options: DropdownItemProps[] = [
    { text: '50', value: '50' },
    { text: '100', value: '100' },
    { text: '500', value: '500' }
];

export interface PaginationFilter {
    limit?: number;
    offset?: number;
}

interface Props {
    filterProps?: PaginationFilter;
    handleLimitChange?: (limit: any) => void;
    handleNext: () => void;
    handlePrev: () => void;
    handleFirst: () => void;
    disablePrevious?: boolean;
    disableNext?: boolean;
    disableFirst?: boolean;
}

interface State {
    filterState: PaginationFilter;
}

const toState = (data?: PaginationFilter): State => {
    return { filterState: data || {} };
};

class Pagination extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = toState(this.props.filterProps);
    }

    render() {
        const { handleLimitChange } = this.props;

        return (
            <>
                {handleLimitChange !== undefined && (
                    <Dropdown
                        compact={true}
                        options={options}
                        defaultValue="50"
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

export default Pagination;
