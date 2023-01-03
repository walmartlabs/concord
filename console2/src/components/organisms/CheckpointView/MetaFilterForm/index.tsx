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
import React, { FunctionComponent } from 'react';
import { Grid, Header, Button, Divider, Popup } from 'semantic-ui-react';

import { useCheckpointContext } from '../Container';

import useQueryParams from '../Container/useQueryParams';
import useForm from '../Container/useForm';
import { usePopup } from '../Container/usePopup';

/**
 * This form uses the metadata config data to allow for filtering of processes
 *
 * On submit, the URL parameters will be updated with the results
 * On load it should pull from state to populate it's data
 */
export const MetaFilterForm: FunctionComponent<{ onClear: () => void }> = ({ onClear }) => {
    const { getMetaDataConfigs, activeFilters, removeAllFilters } = useCheckpointContext();

    const { replaceQueryParams } = useQueryParams();

    // Loop over all possible meta configs and create an object with active filters set
    const initialFormState = getMetaDataConfigs().reduce((previous, current) => {
        // Is the current config currently active?
        if (Object.keys(activeFilters).includes(current.source)) {
            // Found an active filter for a config, set value to the active filter value
            return { ...previous, [current.source]: activeFilters[current.source] };
        } else {
            // No active filter found, set value to empty string
            return { ...previous, [current.source]: '' };
        }
    }, {});

    const { form, setField, clear } = useForm(initialFormState);

    return (
        <div style={{ padding: '8px' }}>
            <Header as="h2">
                <Header.Content>Meta Filters</Header.Content>
                <Header.Subheader>Set filters based on your project metadata</Header.Subheader>
            </Header>
            <Divider style={{ marginBottom: '32px' }} />
            <form
                onSubmit={(e) => {
                    e.preventDefault();
                    replaceQueryParams(form);
                }}>
                {getMetaDataConfigs().map(({ source, caption }) => {
                    return (
                        <Grid key={source} divided="vertically">
                            <Grid.Row
                                columns={2}
                                centered
                                verticalAlign="middle"
                                style={{ padding: '0px' }}>
                                <Grid.Column textAlign="right">
                                    <Header size="small" textAlign="right" color="grey">
                                        {/* Display caption if it exists, otherwise display the source-name } */}
                                        {caption ? caption : source}
                                    </Header>
                                </Grid.Column>
                                <Grid.Column style={{ paddingLeft: '0' }}>
                                    <div className="ui input mini">
                                        <input
                                            type="search"
                                            value={form[source]}
                                            onChange={(e) => setField(source, e.target.value)}
                                        />
                                    </div>
                                </Grid.Column>
                            </Grid.Row>
                        </Grid>
                    );
                })}

                <Grid divided="vertically">
                    <Grid.Row columns="1" centered verticalAlign="bottom">
                        <Button.Group style={{ width: '80%' }}>
                            <Button
                                onClick={() => {
                                    clear();
                                    removeAllFilters();
                                    onClear();
                                }}
                                type="button">
                                Clear
                            </Button>
                            <Button.Or />
                            <Button primary type="submit">
                                Filter
                            </Button>
                        </Button.Group>
                    </Grid.Row>
                </Grid>
            </form>
        </div>
    );
};

export const MetaFilterPopup: FunctionComponent = () => {
    const { canFilter } = useCheckpointContext();
    const { visible, open, close } = usePopup();

    return (
        <Popup
            on={['click']}
            trigger={<Button basic icon="filter" disabled={!canFilter()} />}
            content={<MetaFilterForm onClear={close} />}
            open={visible}
            onClose={close}
            onOpen={open}
            position="bottom right"
            flowing
            wide
            closeOnEscape
            keepInViewPort
            openOnTriggerClick
        />
    );
};
