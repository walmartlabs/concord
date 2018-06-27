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

// Import Semantic UI css globally for all project stories
import 'semantic-ui-css/semantic.min.css';

import * as React from 'react';

import { storiesOf } from '@storybook/react';

storiesOf('Default', module).add('Welcome', () => (
    <a href="https://storybook.js.org/">Main Storybook Website</a>
));
