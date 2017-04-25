import React from "react";
import PropTypes from "prop-types";
import {Table} from "semantic-ui-react";

const dataTable = ({cols, rows, headerFn, cellFn, ...rest}) =>
    <Table singleLine {...rest}>
        <Table.Header>
            <Table.Row>
                {
                    cols.map(c =>
                        <Table.HeaderCell key={c.key} collapsing={c.collapsing} width={c.width}>
                            { headerFn ? headerFn(c.key, c.label) : c.label}
                        </Table.HeaderCell>)

                }
            </Table.Row>
        </Table.Header>
        <Table.Body>
            {
                rows.map((r, idx) =>
                    <Table.Row key={idx}>
                        {
                            cols.map(c => <Table.Cell key={c.key}>
                                {cellFn ? cellFn(r, c.key) : r[c.key]}
                            </Table.Cell>)
                        }
                    </Table.Row>)
            }
        </Table.Body>
    </Table>;

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
