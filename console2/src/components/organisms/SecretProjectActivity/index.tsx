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

import { useCallback, useState } from 'react';

import { ConcordKey, RequestError } from '../../../api/common';
import { updateSecretProject as apiUpdateSecretProject } from '../../../api/org/secret';
import { ProjectSearch, RequestErrorActivity } from '../index';
import { Confirm, Form, Icon, Label } from 'semantic-ui-react';
import { ProjectEntry } from '../../../api/org/project';
import { useDispatch } from 'react-redux';
import { actions } from '../../../state/data/secrets';

interface ExternalProps {
    orgName: ConcordKey;
    projects: ProjectEntry[];
    secretName: ConcordKey;
}

type Props = ExternalProps;

export default ({ orgName, projects, secretName }: Props) => {
    const [dirty, setDirty] = useState<boolean>(false);
    const [showConfirm, setShowConfirm] = useState<boolean>(false);
    const [updating, setUpdating] = useState(false);
    const [error, setError] = useState<RequestError>();
    const [existingProjects, setExistingProjects] = useState<Array<ProjectEntry>>(
        projects.slice(0)
    );
    const [newProjects, setNewProjects] = useState<Array<ProjectEntry>>(projects.slice(0));
    const dispatch = useDispatch();

    const reloadSecret = useCallback(async () => {
        dispatch(actions.getSecret(orgName, secretName));
    },[dispatch, orgName, secretName]);

    const [editMode, setEditMode] = useState<boolean>(false);
    const update = useCallback(async () => {
        try {
            setUpdating(true);
            await apiUpdateSecretProject(
                orgName,
                secretName,
                newProjects.map((project) => project.id)
            );
        } catch (e) {
            setError(e);
        } finally {
            setUpdating(false);
        }
    }, [orgName, secretName, newProjects]);

    const onConfirmHandler = useCallback(async () => {
        await update();
        reloadSecret();
        setShowConfirm(false);
        setDirty(false);
        setEditMode(false);
        setExistingProjects(newProjects);
        
    }, [update, reloadSecret, newProjects]);

    const onCancelHandler = useCallback(() => {
        setShowConfirm(false);
        setNewProjects(existingProjects);
    }, [existingProjects]);

    return (
        <>
            {error && <RequestErrorActivity error={error} />}

            <Form loading={updating}>
                {editMode ? (
                    <Form.Group widths={3}>
                        <Form.Field>
                            <ProjectSearch
                                orgName={orgName}
                                placeholder="Search for projects"
                                fluid={true}
                                onSelect={(project) => {
                                    if (
                                        !newProjects
                                            .map((project) => project.id)
                                            .includes(project.id)
                                    ) {
                                        setNewProjects(newProjects.concat(project));
                                    }
                                }}
                                projectsToNotShow={newProjects}
                                clearOnSelect={true}
                            />
                        </Form.Field>
                    </Form.Group>
                ) : null}
                <Form.Group>
                    <Form.Field>
                        {newProjects && newProjects.length > 0
                            ? newProjects.map((project, index) => (
                                  <Label key={index}>
                                      {project.name}
                                      {editMode ? (
                                          <Icon
                                              name="delete"
                                              onClick={() => {
                                                  if (
                                                      newProjects
                                                          .map((project) => project.id)
                                                          .includes(project.id)
                                                  ) {
                                                      newProjects.splice(
                                                          newProjects
                                                              .map((p) => p.id)
                                                              .indexOf(project.id),
                                                          1
                                                      );
                                                      setNewProjects(newProjects.slice(0));
                                                  }
                                              }}
                                          ></Icon>
                                      ) : null}
                                  </Label>
                              ))
                            : !editMode
                            ? 'No restriction on projects.'
                            : null}
                    </Form.Field>
                </Form.Group>
                <Form.Group>
                    <Form.Button
                        primary={!editMode}
                        negative={editMode}
                        content={editMode ? 'Update' : 'Edit'}
                        disabled={!editMode && dirty}
                        onClick={() => (editMode ? setShowConfirm(true) : setEditMode(true))}
                    />
                    {editMode ? (
                        <Form.Button
                            primary={true}
                            content="Cancel"
                            onClick={() => {
                                setEditMode(false);
                                setNewProjects(existingProjects);
                            }}
                        />
                    ) : null}
                </Form.Group>

                <Confirm
                    open={showConfirm}
                    header="Update the project?"
                    content="Are you sure you want to update the project?"
                    onConfirm={onConfirmHandler}
                    onCancel={onCancelHandler}
                />
            </Form>
        </>
    );
};
