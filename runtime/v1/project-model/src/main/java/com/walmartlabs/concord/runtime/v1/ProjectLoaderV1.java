package com.walmartlabs.concord.runtime.v1;

import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.process.loader.ImportsNormalizer;
import com.walmartlabs.concord.process.loader.ProjectLoader;
import com.walmartlabs.concord.repository.Snapshot;
import com.walmartlabs.concord.runtime.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v1.wrapper.ProcessDefinitionV1;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.List;

import static com.walmartlabs.concord.process.loader.StandardRuntimeTypes.CONCORD_V1_RUNTIME_TYPE;
import static java.util.Objects.requireNonNull;

public class ProjectLoaderV1 implements ProjectLoader {

    private final com.walmartlabs.concord.project.ProjectLoader v1;

    @Inject
    public ProjectLoaderV1(ImportManager importManager) {
        this.v1 = new com.walmartlabs.concord.project.ProjectLoader(requireNonNull(importManager));
    }

    @Override
    public boolean supports(String runtime) {
        return CONCORD_V1_RUNTIME_TYPE.equals(runtime);
    }

    @Override
    public Result loadProject(Path workDir, String runtime, ImportsNormalizer importsNormalizer, ImportsListener listener) throws Exception {
        var v1Result = v1.loadProject(workDir, importsNormalizer::normalize, listener);
        return toCommonResultType(v1Result);
    }

    private static Result toCommonResultType(com.walmartlabs.concord.project.ProjectLoader.Result r) {
        var snapshots = r.getSnapshots();
        var pd = new ProcessDefinitionV1(r.getProjectDefinition());

        return new Result() {
            @Override
            public List<Snapshot> snapshots() {
                return snapshots;
            }

            @Override
            public ProcessDefinition projectDefinition() {
                return pd;
            }
        };
    }
}
