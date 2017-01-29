import React, {Component, PropTypes} from "react";
import {Table} from "semantic-ui-react";

class DataTable extends Component {

    render() {
        const {cols, rows, headerFn, cellFn, ...rest} = this.props;
        return (
            <Table singleLine {...rest}>
                <Table.Header>
                    <Table.Row>
                        {
                            cols.map(c =>
                                <Table.HeaderCell key={c.key}>
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
            </Table>
        );
    }
}

const columnType = PropTypes.shape({
    key: PropTypes.any.isRequired,
    label: PropTypes.string.isRequired
});

DataTable.propTypes = {
    cols: PropTypes.arrayOf(columnType).isRequired,
    rows: PropTypes.arrayOf(PropTypes.object).isRequired,
    headerFn: PropTypes.any,
    cellFn: PropTypes.any
};

export default DataTable;