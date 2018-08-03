import * as React from 'react';
import { Dropdown, DropdownItemProps } from 'semantic-ui-react';
import { DropdownProps } from 'semantic-ui-react/dist/commonjs/modules/Dropdown/Dropdown';
import { ProcessStatus } from '../../../api/process';

const options: DropdownItemProps[] = [
    { key: '', value: undefined, text: 'any' },
    ...Object.keys(ProcessStatus).map((k) => ({
        key: k,
        value: k,
        text: k
    }))
];

class ProcessStatusDropdown extends React.PureComponent<DropdownProps> {
    render() {
        return <Dropdown {...this.props} options={options} placeholder="Status" selection={true} />;
    }
}

export default ProcessStatusDropdown;
