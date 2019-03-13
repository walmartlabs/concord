/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import React, { SFC, useContext, FunctionComponent } from 'react';
import { Label, Icon } from 'semantic-ui-react';
import { CancelButton } from './CancelButton';
import CheckpointContainer from '../Container';
import { Item } from './styles';

type FilterType = { [key: string]: string };

export interface FilterLabelProps {
    caption: string;
    source: string;
    value: string;
}

/**
 * A label for things
 *
 * @param {string} source the key of the label
 * @param {string} value the filter input
 */
export const FilterLabel: FunctionComponent<FilterLabelProps> = ({ caption, source, value }) => {
    const { removeFilter } = useContext(CheckpointContainer.Context);

    return (
        <Label as="a" onClick={() => removeFilter(source)}>
            {caption ? caption : source}:<Label.Detail> {value}</Label.Detail>
            <Icon name="delete" />
        </Label>
    );
};

/**
 * Component Lists all active filters
 */
export const ActiveFilters: FunctionComponent<{}> = () => {
    const { activeFilters, removeAllFilters, getConfigBySourceName } = useContext(
        CheckpointContainer.Context
    );

    if (Object.keys(activeFilters).length > 0) {
        return (
            <Item>
                <span style={{ marginRight: '10px', fontWeight: 'bold', fontSize: '1em' }}>
                    Active filters:
                </span>
                {Object.keys(activeFilters).map((key, index, array) => {
                    const cfg = getConfigBySourceName(key);
                    const caption = cfg ? cfg.caption : key;
                    return (
                        <FilterLabel
                            key={index}
                            caption={caption}
                            source={key}
                            value={activeFilters[key]}
                        />
                    );
                })}

                <CancelButton loading={false} clearFiltersFn={() => removeAllFilters()} />
            </Item>
        );
    } else {
        return null;
    }
};

export default ActiveFilters;
