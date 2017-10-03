// Pulled from Github https://github.com/erikras/redux-form/issues/71#issuecomment-251365333
// Credits to github user asiniy

import React from 'react';

class FileInput extends React.Component {
    
    constructor(props) {
        super(props);
        this.onChange = this.onChange.bind(this);
    }

    onChange(e) {
        const { input: { onChange } } = this.props;
        onChange(e.target.files[0]);
    }

    render() {
        const { input: { value } } = this.props;

        return (
            <input
                type="file"
                onChange={this.onChange}
            />
        );
    }
}

export default FileInput