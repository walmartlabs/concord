package com.walmartlabs.concord.dependencymanager;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.common.ExceptionUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;

import java.io.FileNotFoundException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class ResolveExceptionConverter {

    private final Collection<URI> deps;

    public ResolveExceptionConverter(URI dep) {
        this(Collections.singletonList(dep));
    }

    public ResolveExceptionConverter(Collection<URI> deps) {
        this.deps = deps;
    }

    public DependencyManagerException convert(Exception e) {
        List<Throwable> exceptions = ExceptionUtils.getExceptionList(e);
        String cause = getCause(exceptions);
        return new DependencyManagerException(cause);
    }

    private String getCause(List<Throwable> exceptions) {
        ArtifactResolutionException are = exceptions.stream()
                .filter(ex -> ex instanceof ArtifactResolutionException)
                .map(ex -> (ArtifactResolutionException) ex)
                .reduce((first, second) -> second)
                .orElse(null);

        if (are == null || are.getCause() == null) {
            Throwable t = exceptions.get(0);
            if (t instanceof FileNotFoundException) {
                return "not found " + t.getMessage();
            }
            return t.getMessage();
        }

        if (are.getCause() instanceof ArtifactNotFoundException) {
            return fromNotFoundException((ArtifactNotFoundException) are.getCause());
        } else if (are.getCause() instanceof ArtifactTransferException) {
            return fromTransferException((ArtifactTransferException) are.getCause());
        }
        return exceptions.get(0).getMessage();
    }

    private String fromNotFoundException(ArtifactNotFoundException ane) {
        URI userDependency = deps.stream()
                .filter(d -> isSameDependency(d, ane.getArtifact()))
                .findAny().orElse(null);
        if (userDependency == null) {
            return ane.getMessage();
        }

        String msg = "Could not find artifact '" + userDependency + "'";
        if (ane.getRepository() != null) {
            msg += " in " + ane.getRepository().getUrl();
        }
        return msg;
    }

    private String fromTransferException(ArtifactTransferException ate) {
        URI userDependency = deps.stream()
                .filter(d -> isSameDependency(d, ate.getArtifact()))
                .findAny().orElse(null);
        if (userDependency == null) {
            return ate.getMessage();
        }

        String msg = "Could not transfer artifact '" + userDependency + "'";
        if (ate.getRepository() != null) {
            msg += " from " + ate.getRepository().getUrl();
        }

        if (ate.getCause() == null) {
            return msg;
        }

        String errorDescription;
        if (ate.getCause() instanceof UnknownHostException) {
            errorDescription = "unknown host: " + ate.getCause().getMessage();
        } else {
            errorDescription = ate.getCause().getMessage();
        }

        return msg + ": " + errorDescription;
    }

    private boolean isSameDependency(URI d, Artifact a) {
        String s = d.toString();
        if (s.contains(a.toString())) {
            return true;
        }

        if (!s.contains(a.getArtifactId())) {
            return false;
        }

        if (!s.contains(a.getGroupId())) {
            return false;
        }

        if (!s.contains(a.getVersion())) {
            return false;
        }

        return true;
    }
}
