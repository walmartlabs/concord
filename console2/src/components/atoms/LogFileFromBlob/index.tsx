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
import * as React from 'react';
import { styled } from 'reakit';
import { Highlighter } from '../../../components/molecules';
import { escapeHtml } from '../../../utils';

const TextPre = styled.pre`
    white-space: pre-wrap;
    word-break: break-word;
`;

interface Props {
    blobUrl: string;
    // activeHighlight should highlight the given text
    // with a yellow background
    activeHighlight?: string;
}

interface State {
    data: string[];
    originalData: string;
}

class FileFromBlob extends React.Component<Props, State> {
    state = {
        data: [''],
        originalData: ''
    };

    // TODO: Add polling method to support quicker render for larger files.
    // TODO: Add fetch to log Container
    // * Saga code already supports this.
    async componentDidMount() {
        const blob = await fetch(this.props.blobUrl, { credentials: 'same-origin' }).then((r) =>
            r.blob()
        );
        const reader = new FileReader();

        // This fires after the blob has been read/loaded.
        reader.addEventListener('loadend', (e: any) => {
            const text = e.srcElement.result;
            // Capture text in state
            this.setState({
                originalData: escapeHtml(text),
                data: this.markCheckpointIds(text.split('\n'))
            });
        });

        // Triggers loadend event
        reader.readAsText(blob);
    }

    componentDidUpdate(nextProps: Props, nextState: State) {
        if (nextProps.activeHighlight !== this.props.activeHighlight) {
            this.setState({ data: this.markCheckpointIds(this.state.originalData.split('\n')) });
        }
    }

    markCheckpointIds = (strings: string[]) => {
        strings.forEach((line, index, array) => {
            if (line.includes('Main - checkpoint')) {
                array[index] = this.wrapCheckpoint(line);
            }
            if (this.props.activeHighlight) {
                array[index] = this.wrapActiveHighlight(line);
            }
        });

        return strings;
    };

    wrapCheckpoint = (line: string) => {
        const concordKeyRE = /([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})/g;

        const checkpointId = line.match(concordKeyRE);

        if (checkpointId) {
            return line.replace(
                concordKeyRE,
                `<a style=" font-weight: bold" id="${checkpointId[0]}">${checkpointId[0]}</a>`
            );
        } else {
            console.error(`Could not find regex group for ${checkpointId}`);
            return 'ERROR';
        }
    };

    wrapActiveHighlight = (line: string) => {
        const concordKeyRE = this.props.activeHighlight;
        if (concordKeyRE) {
            const checkpointId = line.match(concordKeyRE);

            if (checkpointId) {
                return line.replace(
                    concordKeyRE,
                    `<a style=" background: yellow; color: black; font-weight: bold" id="${checkpointId[0]}">${checkpointId[0]}</a>`
                );
            } else {
                return line;
            }
        } else {
            console.error(`Could not find regex group for ${concordKeyRE}`);
            return 'ERROR';
        }
    };

    render() {
        const { blobUrl } = this.props;
        const { data } = this.state;

        if (blobUrl) {
            if (data) {
                return (
                    <TextPre>
                        {' '}
                        <Highlighter
                            value={data.join('\n')}
                            config={[
                                { string: 'INFO', style: 'color: #00B5F0' },
                                { string: 'WARN ', style: 'color:  #ffae42' },
                                { string: 'WARNING', style: 'color:  #ffae42' },
                                { string: 'ERROR', style: 'color: #ff0000' },
                                {
                                    string: 'Process status: FINISHED',
                                    style: 'color: green',
                                    divide: true
                                },
                                {
                                    string: 'Process status: FAILED',
                                    style: 'color: #ff0000',
                                    divide: true
                                },
                                {
                                    string: 'Process status: CANCELLED',
                                    style: 'color: #808080',
                                    divide: true
                                },
                                { string: 'ANSIBLE:', style: 'color: #808080' },
                                { string: 'DOCKER:', style: 'color: #808080' }
                            ]}
                        />
                    </TextPre>
                );
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
}

export default FileFromBlob;
