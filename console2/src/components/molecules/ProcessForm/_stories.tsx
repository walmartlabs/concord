/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import { storiesOf } from '@storybook/react';
import { Cardinality, FormFieldType } from '../../../api/process/form';
import ProcessForm from './index';

storiesOf('molecules/ProcessForm', module).add('example', () => (
    <ProcessForm
        onSubmit={(data) => console.log('onSubmit', data)}
        onReturn={() => console.log('onReturn')}
        form={{
            processInstanceId: 'dd88bb86-4d52-11e8-bd2c-7b7285a1cc4d',
            formInstanceId: 'e3dd39c6-4d52-11e8-ac68-bf7f4919ea9c',
            name: 'story',
            fields: [
                {
                    name: 'firstName',
                    label: 'First Name',
                    type: FormFieldType.STRING,
                    cardinality: Cardinality.ONE_AND_ONLY_ONE,
                    value: 'John'
                },
                {
                    name: 'favoriteColor',
                    label: 'Favorite Color',
                    type: FormFieldType.STRING,
                    cardinality: Cardinality.AT_LEAST_ONE,
                    allowedValue: ['red', 'green', 'blue']
                },
                {
                    name: 'country',
                    label: 'Country',
                    type: FormFieldType.STRING,
                    cardinality: Cardinality.ONE_AND_ONLY_ONE,
                    allowedValue: ['us', 'canada', 'mordor']
                },
                {
                    name: 'age',
                    label: 'Age',
                    type: FormFieldType.INT,
                    cardinality: Cardinality.ONE_AND_ONLY_ONE
                },
                {
                    name: 'tos',
                    label: 'Agreed on ToS',
                    type: FormFieldType.BOOLEAN,
                    cardinality: Cardinality.ONE_AND_ONLY_ONE
                },
                {
                    name: 'photo',
                    label: 'Photo',
                    type: FormFieldType.FILE
                }
            ],
            custom: false,
            yield: false
        }}
    />
));
