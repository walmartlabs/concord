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
import { Confirm, Form, Icon, Label } from 'semantic-ui-react';
import { useCallback, useState } from 'react';

import { ConcordKey, RequestError } from '../../../api/common';
import { ProjectEntry } from '../../../api/org/project';
import { updateSecretProject as apiUpdateSecretProject } from '../../../api/org/secret';
import { RequestErrorActivity, ProjectSearch } from '../index';

interface ExternalProps {
    orgName: ConcordKey;
    projects: ProjectEntry[];
    secretName: ConcordKey;
    onUpdated?: () => void;
}

const SecretProjectActivity = ({ orgName, projects, secretName, onUpdated }: ExternalProps) => {
    const [dirty, setDirty] = useState<boolean>(false);
    const [showConfirm, setShowConfirm] = useState<boolean>(false);
    const [updating, setUpdating] = useState(false);
    const [error, setError] = useState<RequestError>();
    const [existingProjects, setExistingProjects] = useState<Array<ProjectEntry>>(projects.slice(0));
    const [newProjects, setNewProjects] = useState<Array<ProjectEntry>>(projects.slice(0));
    const [editMode, setEditMode] = useState<boolean>(false);

    React.useEffect(() => {
        if (!editMode) {
            setExistingProjects(projects.slice(0));
            setNewProjects(projects.slice(0));
        }
    }, [editMode, projects]);

    const update = useCallback(async () => {
        setUpdating(true);
        setError(undefined);

        try {
            await apiUpdateSecretProject(
                orgName,
                secretName,
                newProjects.map((project) => project.id)
            );
        } catch (e) {
            setError(e);
            throw e;
        } finally {
            setUpdating(false);
        }
    }, [orgName, secretName, newProjects]);

    const onConfirmHandler = useCallback(async () => {
        try {
            await update();
            onUpdated && onUpdated();
            setShowConfirm(false);
            setDirty(false);
            setEditMode(false);
            setExistingProjects(newProjects);
        } catch (e) {
            // keep the dialog open so the user can retry or adjust the selection
        }
    }, [newProjects, onUpdated, update]);

    const onCancelHandler = useCallback(() => {
        setShowConfirm(false);
        setNewProjects(existingProjects);
        setDirty(false);
        setError(undefined);
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
                                    if (!newProjects.map((item) => item.id).includes(project.id)) {
                                        setNewProjects(newProjects.concat(project));
                                        setDirty(true);
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
                                                          .map((item) => item.id)
                                                          .includes(project.id)
                                                  ) {
                                                      const nextProjects = newProjects.slice(0);
                                                      nextProjects.splice(
                                                          nextProjects
                                                              .map((item) => item.id)
                                                              .indexOf(project.id),
                                                          1
                                                      );
                                                      setNewProjects(nextProjects);
                                                      setDirty(true);
                                                  }
                                              }}
                                          />
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
                        onClick={() => {
                            if (editMode) {
                                setShowConfirm(true);
                                return;
                            }

                            setError(undefined);
                            setEditMode(true);
                        }}
                    />
                    {editMode ? (
                        <Form.Button
                            primary={true}
                            content="Cancel"
                            onClick={() => {
                                setEditMode(false);
                                setNewProjects(existingProjects);
                                setDirty(false);
                                setError(undefined);
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

export default SecretProjectActivity;
