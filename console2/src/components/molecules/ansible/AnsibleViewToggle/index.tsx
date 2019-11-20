import { default as React } from 'react';
import { CheckboxProps, Popup, Radio } from 'semantic-ui-react';

export interface Props {
    checked: boolean;
    onChange: (event: React.FormEvent<HTMLInputElement>, data: CheckboxProps) => void;
}

export default (props: Props) => {
    return (
        <Popup
            trigger={<Radio label="New view (beta)" toggle={true} {...props} />}
            content="Requires Ansible plugin 1.36.0 or higher"
        />
    );
};
