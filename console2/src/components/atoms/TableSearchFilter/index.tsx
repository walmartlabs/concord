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
import { Button, Dropdown, Grid, Header, Input, Popup } from 'semantic-ui-react';
import { ColumnDefinition } from '../../../api/org';

import './styles.css';

interface State {
    value: string;
    inputValue: string;
    isOpen: boolean;
    filtered: boolean;
}

interface ExternalProps {
    currentValue?: string;
    column: ColumnDefinition;
    onFilterChange: (column: ColumnDefinition, filterValue: string) => void;
}

type Props = ExternalProps;

export default class extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        const value = this.props.currentValue || '';

        this.state = { value, inputValue: value, isOpen: false, filtered: value !== '' };
    }

    componentDidUpdate(prepProps: Props) {
        const currentValue = this.props.currentValue || '';
        const prevValue = prepProps.currentValue || '';
        if (currentValue !== prevValue) {
            this.setState({
                value: currentValue,
                inputValue: currentValue,
                filtered: currentValue !== ''
            });
        }
    }

    renderInput(c: ColumnDefinition) {
        return (
            <Input
                fluid={true}
                name={c.source}
                value={this.state.inputValue}
                onChange={(event, data) => this.setState({ inputValue: data.value })}
                autoFocus={true}
            />
        );
    }

    renderDropDown(c: ColumnDefinition) {
        return (
            <Dropdown
                fluid={true}
                placeholder={'Choose ' + c.caption}
                clearable={true}
                selection={true}
                name={c.source}
                value={this.state.inputValue}
                options={c.searchOptions}
                onChange={(event, data) => this.setState({ inputValue: data.value as string })}
            />
        );
    }

    renderStringField(c: ColumnDefinition) {
        if (c.searchOptions !== undefined) {
            return this.renderDropDown(c);
        } else {
            return this.renderInput(c);
        }
    }

    renderSearchField(c: ColumnDefinition) {
        switch (c.searchValueType!) {
            case 'string': {
                return this.renderStringField(c);
            }
            default:
                return (
                    <p key={c.searchValueType}>Unknown search field type: {c.searchValueType}</p>
                );
        }
    }

    handleOpen() {
        const { value } = this.state;
        this.setState({ isOpen: true, inputValue: value });
    }

    handleClose() {
        this.setState({ isOpen: false });
    }

    handleClearFilter() {
        const { value } = this.state;
        if (value !== '') {
            this.props.onFilterChange(this.props.column, '');
        }
        this.setState({ value: '', inputValue: '', filtered: false });

        this.handleClose();
    }

    handleApplyFilter() {
        const { value, inputValue } = this.state;
        const changed = inputValue !== value;
        if (changed) {
            this.props.onFilterChange(this.props.column, this.state.inputValue);
        }
        this.setState({ value: inputValue, filtered: changed && inputValue !== '' });

        this.handleClose();
    }

    render() {
        const { column } = this.props;
        const { isOpen, filtered, value, inputValue } = this.state;

        return (
            <Popup
                open={isOpen}
                onOpen={() => this.handleOpen()}
                onClose={() => this.handleClose()}
                trigger={
                    <Button
                        style={{ opacity: filtered ? 1 : 0.4 }}
                        className="tableSearchFilter"
                        compact={true}
                        icon={'filter'}
                    />
                }
                on="click">
                <Popup.Content>
                    <Grid textAlign="center" verticalAlign="middle">
                        <Grid.Row>
                            <Grid.Column>
                                <Header as="h4" textAlign="center">
                                    Show items with {column.caption}:
                                </Header>
                            </Grid.Column>
                        </Grid.Row>

                        <Grid.Row>
                            <Grid.Column>{this.renderSearchField(column)}</Grid.Column>
                        </Grid.Row>

                        <Grid.Row>
                            <Grid.Column>
                                <Button.Group fluid={true}>
                                    <Button
                                        onClick={() => this.handleClearFilter()}
                                        disabled={inputValue === ''}>
                                        Clear
                                    </Button>
                                    <Button.Or />
                                    <Button
                                        primary={true}
                                        disabled={value === inputValue}
                                        onClick={() => this.handleApplyFilter()}>
                                        Filter
                                    </Button>
                                </Button.Group>
                            </Grid.Column>
                        </Grid.Row>
                    </Grid>
                </Popup.Content>
            </Popup>
        );
    }
}
