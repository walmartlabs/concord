/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import React from 'react';
import PropTypes from 'prop-types';
import { Table } from 'semantic-ui-react';

const dataTable = ({ cols, rows, headerFn, cellFn, ...rest }) => (
  <Table singleLine {...rest}>
    <Table.Header>
      <Table.Row>
        {cols.map((c) => (
          <Table.HeaderCell key={c.key} collapsing={c.collapsing} width={c.width}>
            {headerFn ? headerFn(c.key, c.label) : c.label}
          </Table.HeaderCell>
        ))}
      </Table.Row>
    </Table.Header>
    <Table.Body>
      {rows.map((r, idx) => (
        <Table.Row key={idx}>
          {cols.map((c) => (
            <Table.Cell key={c.key}>{cellFn ? cellFn(r, c.key) : r[c.key]}</Table.Cell>
          ))}
        </Table.Row>
      ))}
    </Table.Body>
  </Table>
);

const columnType = PropTypes.shape({
  key: PropTypes.any.isRequired,
  label: PropTypes.string,
  collapsing: PropTypes.bool,
  width: PropTypes.number
});

dataTable.propTypes = {
  cols: PropTypes.arrayOf(columnType).isRequired,
  rows: PropTypes.arrayOf(PropTypes.object).isRequired,
  headerFn: PropTypes.any,
  cellFn: PropTypes.any
};

export default dataTable;
